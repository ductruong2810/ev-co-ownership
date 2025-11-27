package com.group8.evcoownership.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * EnvConfig - Configuration class for environment variables
 * Note: .env file is loaded in EvCoOwnershipApplication.main() before Spring Boot starts
 * to ensure environment variables are available during application.properties loading
 */
@Configuration
@Slf4j
public class EnvConfig {
    // Environment variables are loaded in EvCoOwnershipApplication.main()
    // This class is kept for potential future use
}

