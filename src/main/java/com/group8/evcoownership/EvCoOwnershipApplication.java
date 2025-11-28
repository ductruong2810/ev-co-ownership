package com.group8.evcoownership;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class EvCoOwnershipApplication {

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    public static void main(String[] args) {
        // Load .env file trước khi Spring Boot khởi động
        loadEnvFile();

        SpringApplication app = new SpringApplication(EvCoOwnershipApplication.class);
        // Disable shutdown hook for better control
        app.setRegisterShutdownHook(false);
        app.run(args);
    }

    private static void loadEnvFile() {
        try {
            // Load .env file từ root directory của project
            Dotenv dotenv = Dotenv.configure()
                    .directory("./") // Tìm .env ở root của project
                    .ignoreIfMissing() // Không báo lỗi nếu không tìm thấy .env
                    .load();

            // Set tất cả các biến từ .env vào System properties
            dotenv.entries().forEach(entry -> {
                String key = entry.getKey();
                String value = entry.getValue();
                // Chỉ set nếu chưa có trong system properties (ưu tiên system env)
                if (System.getProperty(key) == null && System.getenv(key) == null) {
                    System.setProperty(key, value);
                }
            });

            log.info("Environment variables loaded from .env file successfully");
        } catch (Exception e) {
            log.warn("Could not load .env file: {}. Using system environment variables only.", e.getMessage());
        }
    }
}
