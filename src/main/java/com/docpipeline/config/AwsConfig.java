package com.docpipeline.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.net.URI;

@Configuration
public class AwsConfig {

    private final AppProperties appProperties;

    public AwsConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    private boolean isLocalStack() {
        return appProperties.getAws().getEndpointOverride() != null
                && !appProperties.getAws().getEndpointOverride().isBlank();
    }

    private URI endpointUri() {
        return URI.create(appProperties.getAws().getEndpointOverride());
    }

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(appProperties.getAws().getRegion()));
        if (isLocalStack()) {
            builder.endpointOverride(endpointUri())
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")))
                    .forcePathStyle(true); // required for LocalStack S3
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    @Bean
    public ApplicationRunner initializeS3Bucket(S3Client s3Client) {
        return args -> {
            String bucketName = "docpipeline-documents";

            try {
                // Ensure the bucket exists (essential for LocalStack environments)
                try {
                    s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
                } catch (NoSuchBucketException e) {
                    s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                    System.out.println("Created missing local S3 bucket: " + bucketName);
                }

                // Define the CORS Configuration rules
                CORSRule corsRule = CORSRule.builder()
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("PUT", "POST", "GET", "HEAD")
                        .allowedHeaders("*")
                        .exposeHeaders("ETag")
                        .build();

                CORSConfiguration corsConfig = CORSConfiguration.builder()
                        .corsRules(corsRule)
                        .build();

                // Apply the CORS rules directly to the initialized bucket
                s3Client.putBucketCors(PutBucketCorsRequest.builder()
                        .bucket(bucketName)
                        .corsConfiguration(corsConfig)
                        .build());

                System.out.println("Successfully configured S3 bucket CORS for LocalStack.");

            } catch (Exception e) {
                System.err.println("Failed to initialize S3 settings: " + e.getMessage());
            }
        };
    }

    @Bean
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder()
                .region(Region.of(appProperties.getAws().getRegion()));
        if (isLocalStack()) {
            builder.endpointOverride(endpointUri())
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")))
                    .serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build());
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    @Bean
    public KmsClient kmsClient() {
        var builder = KmsClient.builder()
                .region(Region.of(appProperties.getAws().getRegion()));
        if (isLocalStack()) {
            builder.endpointOverride(endpointUri())
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    @Bean
    public TextractClient textractClient() {
        var builder = TextractClient.builder()
                .region(Region.of(appProperties.getAws().getRegion()));
        if (isLocalStack()) {
            builder.endpointOverride(endpointUri())
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        var builder = SqsAsyncClient.builder()
                .region(Region.of(appProperties.getAws().getRegion()));

        if (isLocalStack()) {
            builder.endpointOverride(endpointUri())
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }
}
