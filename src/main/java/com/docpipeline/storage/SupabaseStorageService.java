package com.docpipeline.storage;

import com.docpipeline.config.AppProperties;
import com.docpipeline.exception.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;

@Service
@Slf4j
public class SupabaseStorageService implements StorageService {

    private final S3Client client;
    private final S3Presigner presigner;
    private final AppProperties properties;

    public SupabaseStorageService(@Qualifier("storageS3Client") S3Client client,
                                  @Qualifier("storageS3Presigner") S3Presigner presigner,
                                  AppProperties properties) {
        this.client = client;
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public String generatePresignedPutUrl(String key, String contentType, Duration expiration) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket()).key(key).contentType(contentType).build();
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(expiration).putObjectRequest(request).build())
                .url().toString();
    }

    @Override
    public String generatePresignedGetUrl(String key, Duration expiration) {
        GetObjectRequest request = GetObjectRequest.builder().bucket(bucket()).key(key).build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(expiration).getObjectRequest(request).build())
                .url().toString();
    }

    @Override
    public boolean doesObjectExist(String key) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket()).key(key).build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw storageFailure("check object existence", key, exception);
        }
    }

    @Override
    public long getObjectSize(String key) {
        try {
            HeadObjectResponse response = client.headObject(
                    HeadObjectRequest.builder().bucket(bucket()).key(key).build());
            return response.contentLength();
        } catch (S3Exception exception) {
            throw storageFailure("read object metadata", key, exception);
        }
    }

    @Override
    public InputStream openObject(String key) {
        try {
            ResponseInputStream<GetObjectResponse> stream = client.getObject(
                    GetObjectRequest.builder().bucket(bucket()).key(key).build());
            return stream;
        } catch (S3Exception exception) {
            throw storageFailure("open object", key, exception);
        }
    }

    @Override
    public void putString(String key, String contentType, String value) {
        try {
            client.putObject(PutObjectRequest.builder()
                            .bucket(bucket()).key(key).contentType(contentType).build(),
                    RequestBody.fromString(value));
        } catch (S3Exception exception) {
            throw storageFailure("write object", key, exception);
        }
    }

    @Override
    public void deleteObject(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(key).build());
        } catch (S3Exception exception) {
            throw storageFailure("delete object", key, exception);
        }
    }

    private String bucket() {
        return properties.getStorage().getBucketName();
    }

    private StorageException storageFailure(String operation, String key, S3Exception cause) {
        log.error("Failed to {} {}", operation, key, cause);
        return new StorageException("Failed to " + operation, cause);
    }
}
