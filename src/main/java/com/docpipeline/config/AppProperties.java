package com.docpipeline.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Aws aws = new Aws();
    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();

    @Getter
    @Setter
    public static class Aws {
        private String region = "ap-south-1";
        private String endpointOverride; // set to http://localhost:4566 for LocalStack
        private S3 s3 = new S3();
        private Kms kms = new Kms();
        private Sqs sqs = new Sqs();

        @Getter
        @Setter
        public static class S3 {
            private String bucketName;
            private Duration presignedUrlExpiration = Duration.ofMinutes(15);
        }

        @Getter
        @Setter
        public static class Kms {
            private String keyId;
        }

        @Getter
        @Setter
        public static class Sqs {
            private String queueName;
        }
    }

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long expiration = 86400000; // 24 hours
    }

    @Getter
    @Setter
    public static class Cors {
        private String allowedOrigins = "http://localhost:5173,http://localhost:3000";
    }
}
