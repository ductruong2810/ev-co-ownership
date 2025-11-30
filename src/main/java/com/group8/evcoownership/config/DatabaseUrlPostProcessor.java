package com.group8.evcoownership.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DatabaseUrlPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        log.info("DatabaseUrlPostProcessor: Starting environment post-processing");
        
        // Check all possible ways to get active profile
        String activeProfile = environment.getProperty("spring.profiles.active", "");
        if (activeProfile.isEmpty()) {
            activeProfile = System.getProperty("spring.profiles.active", "");
        }
        if (activeProfile.isEmpty()) {
            activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        }
        
        log.info("DatabaseUrlPostProcessor: Active profile = '{}'", activeProfile);
        
        // Only process for production profile
        if (!"prod".equals(activeProfile)) {
            log.info("DatabaseUrlPostProcessor: Skipping - not production profile");
            return;
        }

        String datasourceUrl = environment.getProperty("spring.datasource.url");
        if (datasourceUrl == null || datasourceUrl.isEmpty()) {
            datasourceUrl = System.getenv("SPRING_DATASOURCE_URL");
        }
        
        if (datasourceUrl == null || datasourceUrl.isEmpty()) {
            log.error("DatabaseUrlPostProcessor: SPRING_DATASOURCE_URL is not set!");
            return;
        }

        log.info("DatabaseUrlPostProcessor: Original URL = {}", 
            datasourceUrl.replaceAll("password=[^&;?]*", "password=***")
                        .replaceAll(":[^@]*@", ":***@"));

        // Check if URL already contains sslmode parameter
        if (!datasourceUrl.contains("sslmode=")) {
            // Add sslmode=require to URL
            String separator = datasourceUrl.contains("?") ? "&" : "?";
            String updatedUrl = datasourceUrl + separator + "sslmode=require";
            
            log.info("DatabaseUrlPostProcessor: Added sslmode=require to database URL");
            log.info("DatabaseUrlPostProcessor: Updated URL = {}", 
                updatedUrl.replaceAll("password=[^&;?]*", "password=***")
                        .replaceAll(":[^@]*@", ":***@"));
            
            // Update the property
            Map<String, Object> properties = new HashMap<>();
            properties.put("spring.datasource.url", updatedUrl);
            environment.getPropertySources().addFirst(
                new MapPropertySource("database-url-override", properties)
            );
            
            log.info("DatabaseUrlPostProcessor: Successfully updated database URL");
        } else {
            log.info("DatabaseUrlPostProcessor: Database URL already contains sslmode parameter");
        }
    }
}

