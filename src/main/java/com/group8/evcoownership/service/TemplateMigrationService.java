package com.group8.evcoownership.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Service
@Slf4j
public class TemplateMigrationService {

    private BlobContainerClient blobContainerClient;

    public TemplateMigrationService(BlobContainerClient blobContainerClient) {
        this.blobContainerClient = blobContainerClient;
        log.info("TemplateMigrationService initialized");
    }

    // For test profile - no BlobContainerClient injection
    public TemplateMigrationService() {
        log.info("TemplateMigrationService initialized (test mode)");
    }

    @Value("${azure.storage.template-blob-name:contract-template.html}")
    private String templateBlobName;

    /**
     * Migrate template từ file local vào Azure Blob Storage
     */
    public void migrateTemplateToAzure() {
        if (blobContainerClient == null) {
            log.warn("BlobContainerClient is null, mock migration");
            return;
        }

        try {
            // Đọc template từ file local
            ClassPathResource resource = new ClassPathResource("Ev_Contract_Template.html");
            String htmlContent = new String(Files.readAllBytes(resource.getFile().toPath()));

            // Upload vào Azure Blob Storage
            BlobClient blobClient = blobContainerClient.getBlobClient(templateBlobName);

            ByteArrayInputStream inputStream = new ByteArrayInputStream(htmlContent.getBytes(StandardCharsets.UTF_8));

            BlobHttpHeaders headers = new BlobHttpHeaders()
                    .setContentType("text/html; charset=utf-8");

            blobClient.upload(inputStream, htmlContent.length(), true);
            blobClient.setHttpHeaders(headers);

            log.info("✅ Template đã được migrate thành công vào Azure Blob Storage!");
            log.info("Blob URL: {}", blobClient.getBlobUrl());

        } catch (IOException e) {
            log.error("❌ Lỗi khi migrate template: {}", e.getMessage());
            throw new RuntimeException("Failed to migrate template to Azure", e);
        }
    }

    /**
     * Kiểm tra template có tồn tại trong Azure không
     */
    public boolean isTemplateExistsInAzure() {
        if (blobContainerClient == null) {
            log.warn("BlobContainerClient is null, mock exists check");
            return true; // Mock: template always exists
        }

        try {
            BlobClient blobClient = blobContainerClient.getBlobClient(templateBlobName);
            return blobClient.exists();
        } catch (Exception e) {
            log.error("Error checking template existence: {}", e.getMessage());
            return false;
        }
    }
}