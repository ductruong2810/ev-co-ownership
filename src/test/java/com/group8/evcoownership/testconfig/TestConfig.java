package com.group8.evcoownership.testconfig;

import com.azure.storage.blob.BlobContainerClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import static org.mockito.Mockito.mock;

@TestConfiguration
@Profile("test")
public class TestConfig {

    @Bean
    @Primary
    public BlobContainerClient mockBlobContainerClient() {
        return mock(BlobContainerClient.class);
    }
}
