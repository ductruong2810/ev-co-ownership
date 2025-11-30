package com.group8.evcoownership.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

@Configuration
@Slf4j
@Profile("prod")
public class DataSourceConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Bean
    @Primary
    public DataSource dataSource() {
        log.info("Configuring DataSource for production environment");
        
        // Ensure SSL is enabled for Supabase
        String finalUrl = jdbcUrl;
        if (!jdbcUrl.contains("sslmode=")) {
            // Add sslmode=require if not present
            String separator = jdbcUrl.contains("?") ? "&" : "?";
            finalUrl = jdbcUrl + separator + "sslmode=require";
            log.info("Added sslmode=require to database URL");
        } else {
            log.info("Database URL already contains sslmode parameter");
        }
        
        log.info("Database URL: {}", finalUrl.replace(password, "***"));
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(finalUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        
        // Connection pool settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);
        
        // PostgreSQL specific settings for Supabase
        // SSL is already configured in the URL, but we ensure it's set
        if (!finalUrl.contains("sslmode=")) {
            config.addDataSourceProperty("sslmode", "require");
        }
        
        log.info("DataSource configured successfully");
        return new HikariDataSource(config);
    }
}

