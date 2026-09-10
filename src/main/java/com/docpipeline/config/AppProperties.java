package com.docpipeline.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Supabase supabase = new Supabase();
    private Storage storage = new Storage();
    private Aws aws = new Aws();
    private Processing processing = new Processing();
    private Cors cors = new Cors();

    @Getter
    @Setter
    public static class Supabase {
        private String url = "http://localhost:54321";
        private String publishableKey;
        private String secretKey;
        private String jwtIssuer = "http://localhost:54321/auth/v1";
        private String jwtAudience = "authenticated";
    }

    @Getter
    @Setter
    public static class Storage {
        private String endpoint = "http://localhost:54321/storage/v1/s3";
        private String region = "local";
        private String accessKeyId;
        private String secretAccessKey;
        private String bucketName = "docpipeline-private";
        private Duration presignedUrlExpiration = Duration.ofMinutes(15);
    }

    @Getter
    @Setter
    public static class Aws {
        private String region = "ap-south-1";
        private String textractStagingBucket;
        private String textractKmsKeyId;
    }

    @Getter
    @Setter
    public static class Processing {
        private int maxAttempts = 3;
        private int visibilityTimeoutSeconds = 300;
        private int batchSize = 5;
        private Duration pollInterval = Duration.ofSeconds(5);
        private Duration reconciliationAge = Duration.ofMinutes(5);
    }

    @Getter
    @Setter
    public static class Cors {
        private String allowedOrigins = "http://localhost:5173";
    }
}
