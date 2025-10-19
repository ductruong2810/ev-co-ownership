package com.group8.evcoownership.config;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
public class TestAzureBlobConfig {

    @Bean
    public BlobServiceClient blobServiceClient() {
        return Mockito.mock(BlobServiceClient.class);
    }

    @Bean
    public BlobContainerClient blobContainerClient() {
        return Mockito.mock(BlobContainerClient.class);
    }
}


