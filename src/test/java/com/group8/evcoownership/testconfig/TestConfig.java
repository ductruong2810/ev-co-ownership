package com.group8.evcoownership.testconfig;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.s3.S3Client;

import static org.mockito.Mockito.mock;

@TestConfiguration
@Profile("test")
public class TestConfig {

    @Bean
    @Primary
    public S3Client r2S3Client() {
        return mock(S3Client.class);
    }
}
