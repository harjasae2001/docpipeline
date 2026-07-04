package com.docpipeline.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.textract.TextractClient;

import java.net.URI;

@Configuration
public class AwsConfig {

    private final AppProperties appProperties;

    public AwsConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /** True when an endpoint override is configured (i.e. using LocalStack). */
    private boolean isLocalStack() {
        String ep = appProperties.getAws().getEndpointOverride();
        return ep != null && !ep.isBlank();
    }

    /**
     * Endpoint for SDK API calls (container → container when dockerised).
     * e.g. http://localstack:4566
     */
    private URI apiEndpoint() {
        return URI.create(appProperties.getAws().getEndpointOverride());
    }

    /**
     * Endpoint baked into presigned URLs (must be reachable by the browser).
     * Falls back to the API endpoint when presignerEndpointOverride is not set
     * (safe for pure-local dev where both are localhost:4566).
     * e.g. http://localhost:4566
     */
    private URI presignerEndpoint() {
        String presignerEp = appProperties.getAws().getPresignerEndpointOverride();
        if (presignerEp != null && !presignerEp.isBlank()) {
            return URI.create(presignerEp);
        }
        return apiEndpoint();
    }

    private static final StaticCredentialsProvider LOCALSTACK_CREDS =
            StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));

    /**
     * S3Client — used for HeadObject, DeleteObject, etc.
     * Points to the container-internal LocalStack hostname when dockerised.
     */
    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(appProperties.getAws().getRegion()));
        if (isLocalStack()) {
            builder.endpointOverride(apiEndpoint())
                    .credentialsProvider(LOCALSTACK_CREDS)
                    .forcePathStyle(true); // required for LocalStack
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    /**
     * S3Presigner — generates presigned PUT/GET URLs that are handed to the browser.
     * Must point to a URL the browser can reach: localhost:4566 (not the Docker hostname).
     */
    @Bean
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder()
                .region(Region.of(appProperties.getAws().getRegion()));
        if (isLocalStack()) {
            builder.endpointOverride(presignerEndpoint()) // ← browser-accessible URL
                    .credentialsProvider(LOCALSTACK_CREDS);
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
            builder.endpointOverride(apiEndpoint())
                    .credentialsProvider(LOCALSTACK_CREDS);
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
            builder.endpointOverride(apiEndpoint())
                    .credentialsProvider(LOCALSTACK_CREDS);
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }
}
