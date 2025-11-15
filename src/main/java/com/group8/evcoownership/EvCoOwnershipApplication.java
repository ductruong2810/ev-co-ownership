package com.group8.evcoownership;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class EvCoOwnershipApplication {

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvCoOwnershipApplication.class);
        // Disable shutdown hook to prevent NoClassDefFoundError on Azure
        // Azure App Service handles shutdown gracefully without needing Spring's shutdown hook
        app.setRegisterShutdownHook(false);
        app.run(args);
    }
}
