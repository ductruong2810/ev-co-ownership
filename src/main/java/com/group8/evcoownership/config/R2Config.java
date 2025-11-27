package com.group8.evcoownership.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@Slf4j
public class R2Config {

    @Value("${cloudflare.r2.endpoint:}")
    private String endpoint;

    @Value("${cloudflare.r2.access-key:}")
    private String accessKeyId;

    @Value("${cloudflare.r2.secret-key:}")
    private String secretAccessKey;

    @Value("${cloudflare.r2.enabled:false}")
    private boolean enabled;

    @Bean
    public S3Client r2S3Client() {
        if (!enabled || endpoint == null || endpoint.isEmpty() || 
            accessKeyId == null || accessKeyId.isEmpty() ||
            secretAccessKey == null || secretAccessKey.isEmpty()) {
            log.warn("R2 is not enabled or credentials are missing. R2 S3Client will not be created.");
            return null;
        }

        log.info("Initializing Cloudflare R2 S3Client");
        log.info("R2 Endpoint: {}", endpoint);

        try {
            S3Client client = S3Client.builder()
                    .region(Region.US_EAST_1) // R2 uses a fake region
                    .endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(
                            S3Configuration.builder()
                                    .pathStyleAccessEnabled(true) // R2 requires path-style access
                                    .build()
                    )
                    .credentialsProvider(
                            StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                            )
                    )
                    .build();

            log.info("R2 S3Client initialized successfully!");
            return client;
        } catch (Exception e) {
            log.error("Failed to initialize R2 S3Client: {}", e.getMessage(), e);
            return null;
        }
    }
}

