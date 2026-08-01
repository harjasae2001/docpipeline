package com.docpipeline.storage;

import com.docpipeline.config.AppProperties;
import com.docpipeline.exception.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Service
@Slf4j
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AppProperties appProperties;

    public S3StorageService(S3Client s3Client, S3Presigner s3Presigner, AppProperties appProperties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.appProperties = appProperties;
    }

    public String generatePresignedPutUrl(String s3Key, String contentType, Duration expiration) {
        try {
            // NOTE: Do NOT include serverSideEncryption() or ssekmsKeyId() here.
            // Including SSE headers in a pre-signed PUT URL forces the browser to send
            // x-amz-server-side-encryption and x-amz-server-side-encryption-aws-kms-key-id
            // headers in the XHR request, which causes a 403 when they are omitted.
            // KMS encryption is enforced automatically by the S3 bucket's default
            // encryption policy (configured in Terraform), so the client does not need
            // to request it explicitly.
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(appProperties.getAws().getS3().getBucketName())
                    .key(s3Key)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .putObjectRequest(putObjectRequest)
                    .build();

            String url = s3Presigner.presignPutObject(presignRequest).url().toString();
            log.debug("Generated presigned PUT URL for key: {}", s3Key);
            return url;
        } catch (S3Exception e) {
            log.error("Failed to generate presigned PUT URL for key: {}", s3Key, e);
            throw new StorageException("Failed to generate upload URL", e);
        }
    }

    public String generatePresignedGetUrl(String s3Key, Duration expiration) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(appProperties.getAws().getS3().getBucketName())
                    .key(s3Key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(getObjectRequest)
                    .build();

            String url = s3Presigner.presignGetObject(presignRequest).url().toString();
            log.debug("Generated presigned GET URL for key: {}", s3Key);
            return url;
        } catch (S3Exception e) {
            log.error("Failed to generate presigned GET URL for key: {}", s3Key, e);
            throw new StorageException("Failed to generate download URL", e);
        }
    }

    public boolean doesObjectExist(String s3Key) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(appProperties.getAws().getS3().getBucketName())
                    .key(s3Key)
                    .build();
            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            log.error("Error checking object existence for key: {}", s3Key, e);
            throw new StorageException("Failed to check object existence", e);
        }
    }

    public void deleteObject(String s3Key) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(appProperties.getAws().getS3().getBucketName())
                    .key(s3Key)
                    .build();
            s3Client.deleteObject(deleteRequest);
            log.info("Deleted S3 object: {}", s3Key);
        } catch (S3Exception e) {
            log.error("Failed to delete S3 object: {}", s3Key, e);
            throw new StorageException("Failed to delete object from S3", e);
        }
    }

    public long getObjectSize(String s3Key) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(appProperties.getAws().getS3().getBucketName())
                    .key(s3Key)
                    .build();
            HeadObjectResponse response = s3Client.headObject(headRequest);
            return response.contentLength();
        } catch (S3Exception e) {
            log.error("Failed to get object size for key: {}", s3Key, e);
            throw new StorageException("Failed to get object size", e);
        }
    }
}
