package com.docpipeline.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
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

    /**
     * LOCAL DEVELOPMENT ONLY — Creates and configures the S3 bucket in LocalStack
     * on startup, since LocalStack does not persist state between restarts.
     *
     * In production (ECS), this bean is NOT created. The S3 bucket is provisioned
     * and owned by Terraform. The application only reads/writes objects; it never
     * creates, deletes, or reconfigures the bucket itself.
     *
     * CORS for the production bucket is managed by the Terraform S3 module.
     */
    @Bean
    @Profile("local")
    public ApplicationRunner initializeS3Bucket(S3Client s3Client) {
        return args -> {
            String bucketName = appProperties.getAws().getS3().getBucketName();

            try {
                try {
                    s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
                    System.out.println("S3 bucket already exists (LocalStack): " + bucketName);
                } catch (NoSuchBucketException e) {
                    s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                    System.out.println("Created S3 bucket in LocalStack: " + bucketName);
                }

                // LocalStack CORS — allows the Vite dev server to make PUT requests
                CORSRule corsRule = CORSRule.builder()
                        .allowedOrigins("http://localhost:5173", "http://localhost:3000")
                        .allowedMethods("PUT", "POST", "GET", "HEAD", "DELETE")
                        .allowedHeaders("*")
                        .exposeHeaders("ETag")
                        .build();

                s3Client.putBucketCors(PutBucketCorsRequest.builder()
                        .bucket(bucketName)
                        .corsConfiguration(CORSConfiguration.builder().corsRules(corsRule).build())
                        .build());

                System.out.println("Configured LocalStack S3 CORS for bucket: " + bucketName);

            } catch (Exception e) {
                System.err.println("[LOCAL] Failed to initialize S3 bucket: " + e.getMessage());
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
