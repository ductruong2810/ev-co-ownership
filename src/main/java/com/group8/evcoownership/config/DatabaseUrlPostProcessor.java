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
        String activeProfile = environment.getProperty("spring.profiles.active", "");
        
        // Only process for production profile
        if (!"prod".equals(activeProfile)) {
            return;
        }

        String datasourceUrl = environment.getProperty("spring.datasource.url");
        
        if (datasourceUrl == null || datasourceUrl.isEmpty()) {
            log.warn("SPRING_DATASOURCE_URL is not set");
            return;
        }

        // Check if URL already contains sslmode parameter
        if (!datasourceUrl.contains("sslmode=")) {
            // Add sslmode=require to URL
            String separator = datasourceUrl.contains("?") ? "&" : "?";
            String updatedUrl = datasourceUrl + separator + "sslmode=require";
            
            log.info("Added sslmode=require to database URL for Supabase connection");
            log.debug("Original URL: {}", datasourceUrl.replaceAll("password=[^&;]*", "password=***"));
            log.debug("Updated URL: {}", updatedUrl.replaceAll("password=[^&;]*", "password=***"));
            
            // Update the property
            Map<String, Object> properties = new HashMap<>();
            properties.put("spring.datasource.url", updatedUrl);
            environment.getPropertySources().addFirst(
                new MapPropertySource("database-url-override", properties)
            );
        } else {
            log.info("Database URL already contains sslmode parameter");
        }
    }
}

