package com.group8.evcoownership.config;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@Slf4j
public class AzureBlobConfig {

    @Value("${azure.storage.connection-string:}")
    private String connectionString;

    @Value("${azure.storage.container-name:}")
    private String containerName;

    @Bean
    @Primary
    public BlobServiceClient blobServiceClient() {
        if (connectionString == null || connectionString.trim().isEmpty()) {
            log.warn("Azure Storage connection string is not configured. Using mock configuration.");
            return new BlobServiceClientBuilder()
                    .connectionString("DefaultEndpointsProtocol=https;AccountName=test;AccountKey=test;EndpointSuffix=core.windows.net")
                    .buildClient();
        }

        log.info("Initializing Azure Blob Service Client with configured connection string");
        return new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
    }

    @Bean
    @Primary
    public BlobContainerClient blobContainerClient(BlobServiceClient blobServiceClient) {
        String container;
        if (containerName == null || containerName.trim().isEmpty()) {
            // Default fallback for development
            container = "test-container";
        } else {
            container = containerName;
        }
        log.info("Initializing Azure Blob Container Client with container: {}", container);
        return blobServiceClient.getBlobContainerClient(container);
    }
}