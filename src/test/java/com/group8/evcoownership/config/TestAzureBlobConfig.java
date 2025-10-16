package com.group8.evcoownership.config;

import com.azure.storage.blob.BlobContainerClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
public class TestAzureBlobConfig {

    @Bean
    public BlobContainerClient blobContainerClient() {
        return Mockito.mock(BlobContainerClient.class);
    }
}


