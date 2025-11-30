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

        String updatedUrl = datasourceUrl;
        boolean urlChanged = false;

        // Fix port: Supabase direct connection (5432) should use pooler (6543) for production
        // Check if URL contains direct connection port 5432
        if (updatedUrl.contains(":5432/") || updatedUrl.contains(":5432?")) {
            log.warn("DatabaseUrlPostProcessor: Detected direct connection port 5432. Converting to pooler port 6543 for better reliability.");
            updatedUrl = updatedUrl.replace(":5432/", ":6543/").replace(":5432?", ":6543?");
            urlChanged = true;
        }
        
        // Check if host is direct connection format (db.xxx.supabase.co) and convert to pooler
        if (updatedUrl.contains("db.") && updatedUrl.contains(".supabase.co")) {
            log.warn("DatabaseUrlPostProcessor: Detected direct connection host. Consider using pooler host (xxx.pooler.supabase.com) for production.");
            // Extract project ref from db.xxx.supabase.co and suggest pooler format
            // This is just a warning, we'll keep the URL as is but log the suggestion
        }

        // Check if URL already contains sslmode parameter
        if (!updatedUrl.contains("sslmode=")) {
            // Add sslmode=require to URL
            String separator = updatedUrl.contains("?") ? "&" : "?";
            updatedUrl = updatedUrl + separator + "sslmode=require";
            urlChanged = true;
            log.info("DatabaseUrlPostProcessor: Added sslmode=require to database URL");
        } else {
            log.info("DatabaseUrlPostProcessor: Database URL already contains sslmode parameter");
        }

        // Update the property if URL was changed
        if (urlChanged) {
            log.info("DatabaseUrlPostProcessor: Updated URL = {}", 
                updatedUrl.replaceAll("password=[^&;?]*", "password=***")
                        .replaceAll(":[^@]*@", ":***@"));
            
            Map<String, Object> properties = new HashMap<>();
            properties.put("spring.datasource.url", updatedUrl);
            environment.getPropertySources().addFirst(
                new MapPropertySource("database-url-override", properties)
            );
            
            log.info("DatabaseUrlPostProcessor: Successfully updated database URL");
        } else {
            log.info("DatabaseUrlPostProcessor: No changes needed to database URL");
        }
    }
}

