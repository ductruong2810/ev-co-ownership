package com.group8.evcoownership.config;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobContainerAccessPolicies;
import com.azure.storage.blob.models.PublicAccessType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@Slf4j
public class AzureBlobConfig {

    @Value("${azure.storage.container-name}")
    private String containerName;

    @Value("${azure.storage.connection-string}")
    private String connectionString;

    @Bean
    public BlobServiceClient blobServiceClient() {
        if (connectionString == null || connectionString.trim().isEmpty()) {
            throw new IllegalStateException("Azure connection string is not configured!");
        }

        BlobServiceClient client = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();

        log.info("BlobServiceClient created successfully!");
        return client;
    }

    @Bean
    public BlobContainerClient blobContainerClient(BlobServiceClient blobServiceClient) {
        if (containerName == null || containerName.trim().isEmpty()) {
            throw new IllegalStateException("Azure container name is not configured!");
        }

        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);

        // CREATE CONTAINER IF NOT EXISTS
        if (!containerClient.exists()) {
            log.info("Container does NOT exist, creating: {}", containerName);
            containerClient.create();
            log.info("Container created successfully: {}", containerName);
        } else {
            log.info("Container already exists: {}", containerName);
        }

        // SET PUBLIC ACCESS TO BLOB (IMPORTANT!)
        try {
            BlobContainerAccessPolicies accessPolicies = containerClient.getAccessPolicy();
            PublicAccessType currentAccess = accessPolicies.getBlobAccessType();

            if (currentAccess != PublicAccessType.BLOB) {
                log.info("Setting public access to BLOB for container: {}", containerName);
                containerClient.setAccessPolicy(PublicAccessType.BLOB, null);
                log.info("Public access set to BLOB successfully");
            } else {
                log.info("Container already has BLOB public access");
            }
        } catch (Exception e) {
            log.warn("Could not set public access (may need Azure portal config): {}", e.getMessage());
        }

        return containerClient;
    }
}
