package com.group8.evcoownership.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class TemplateService {

    private BlobContainerClient blobContainerClient;

    public TemplateService(BlobContainerClient blobContainerClient) {
        this.blobContainerClient = blobContainerClient;
        log.info("TemplateService initialized");
    }

    // For test profile - no BlobContainerClient injection
    public TemplateService() {
        log.info("TemplateService initialized (test mode)");
    }

    @Value("${azure.storage.template-blob-name:contract-template.html}")
    private String templateBlobName;

    /**
     * Lấy nội dung template hiện tại từ Azure Blob Storage
     */
    public String getTemplateContent() {
        if (blobContainerClient == null) {
            log.warn("BlobContainerClient is null, returning default template");
            return getDefaultTemplateContent();
        }

        try {
            BlobClient blobClient = blobContainerClient.getBlobClient(templateBlobName);

            if (!blobClient.exists()) {
                log.warn("Template blob not found: {}", templateBlobName);
                return getDefaultTemplateContent();
            }

            String content = blobClient.downloadContent().toString();
            log.info("Template loaded from Azure Blob Storage: {}", templateBlobName);
            return content;

        } catch (Exception e) {
            log.error("Error reading template from Azure: {}", e.getMessage());
            return getDefaultTemplateContent();
        }
    }

    /**
     * Cập nhật nội dung template trong Azure Blob Storage với chunked upload (MultipartFile)
     */
    public Map<String, Object> updateTemplateContentChunked(MultipartFile file) {
        if (blobContainerClient == null) {
            log.warn("BlobContainerClient is null, mock update");
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Template đã được cập nhật thành công (mock)");
            result.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            result.put("blobName", templateBlobName);
            result.put("blobUrl", "mock://storage.com/" + templateBlobName);
            result.put("uploadMethod", "mock");
            result.put("contentSize", file.getSize());
            return result;
        }

        try {
            BlobClient blobClient = blobContainerClient.getBlobClient(templateBlobName);

            // Sử dụng BlockBlobClient cho chunked upload
            com.azure.storage.blob.specialized.BlockBlobClient blockBlobClient = blobContainerClient.getBlobClient(templateBlobName).getBlockBlobClient();

            // Upload với chunked approach
            long fileSize = file.getSize();

            // Nếu file nhỏ (< 4MB), upload trực tiếp
            if (fileSize < 4 * 1024 * 1024) {
                blockBlobClient.upload(file.getInputStream(), fileSize, true);
            } else {
                // Nếu file lớn, sử dụng staged upload
                String blockId = java.util.Base64.getEncoder().encodeToString("block1".getBytes());
                blockBlobClient.stageBlock(blockId, file.getInputStream(), fileSize);
                blockBlobClient.commitBlockList(java.util.Arrays.asList(blockId));
            }

            // Set headers
            BlobHttpHeaders headers = new BlobHttpHeaders()
                    .setContentType(file.getContentType());
            blobClient.setHttpHeaders(headers);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Template đã được cập nhật thành công trong Azure Blob Storage (chunked)");
            result.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            result.put("blobName", templateBlobName);
            result.put("blobUrl", blobClient.getBlobUrl());
            result.put("uploadMethod", "chunked");
            result.put("contentSize", fileSize);

            log.info("Template updated in Azure Blob Storage (chunked): {}", blobClient.getBlobUrl());
            return result;

        } catch (Exception e) {
            log.error("Error updating template in Azure (chunked): {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Có lỗi khi cập nhật template (chunked): " + e.getMessage());
            return result;
        }
    }

    /**
     * Cập nhật nội dung template trong Azure Blob Storage với chunked upload (String)
     */
    public Map<String, Object> updateTemplateContentChunked(String newContent) {
        if (blobContainerClient == null) {
            log.warn("BlobContainerClient is null, mock update");
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Template đã được cập nhật thành công (mock)");
            result.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            result.put("blobName", templateBlobName);
            result.put("blobUrl", "mock://storage.com/" + templateBlobName);
            result.put("uploadMethod", "mock");
            result.put("contentSize", newContent.length());
            return result;
        }

        try {
            BlobClient blobClient = blobContainerClient.getBlobClient(templateBlobName);

            // Sử dụng BlockBlobClient cho chunked upload
            com.azure.storage.blob.specialized.BlockBlobClient blockBlobClient = blobContainerClient.getBlobClient(templateBlobName).getBlockBlobClient();

            // Upload với chunked approach
            byte[] contentBytes = newContent.getBytes(StandardCharsets.UTF_8);

            // Nếu file nhỏ (< 4MB), upload trực tiếp
            if (contentBytes.length < 4 * 1024 * 1024) {
                blockBlobClient.upload(new ByteArrayInputStream(contentBytes), contentBytes.length, true);
            } else {
                // Nếu file lớn, sử dụng staged upload
                String blockId = java.util.Base64.getEncoder().encodeToString("block1".getBytes());
                blockBlobClient.stageBlock(blockId, new ByteArrayInputStream(contentBytes), contentBytes.length);
                blockBlobClient.commitBlockList(java.util.Arrays.asList(blockId));
            }

            // Set headers
            BlobHttpHeaders headers = new BlobHttpHeaders()
                    .setContentType("text/html; charset=utf-8");
            blobClient.setHttpHeaders(headers);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Template đã được cập nhật thành công trong Azure Blob Storage (chunked)");
            result.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            result.put("blobName", templateBlobName);
            result.put("blobUrl", blobClient.getBlobUrl());
            result.put("uploadMethod", "chunked");
            result.put("contentSize", contentBytes.length);

            log.info("Template updated in Azure Blob Storage (chunked): {}", blobClient.getBlobUrl());
            return result;

        } catch (Exception e) {
            log.error("Error updating template in Azure (chunked): {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Có lỗi khi cập nhật template (chunked): " + e.getMessage());
            return result;
        }
    }

    /**
     * Cập nhật nội dung template trong Azure Blob Storage (method cũ)
     */
    public Map<String, Object> updateTemplateContent(String newContent) {
        if (blobContainerClient == null) {
            log.warn("BlobContainerClient is null, mock update");
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Template đã được cập nhật thành công (mock)");
            result.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            result.put("blobName", templateBlobName);
            result.put("blobUrl", "mock://storage.com/" + templateBlobName);
            return result;
        }

        try {
            BlobClient blobClient = blobContainerClient.getBlobClient(templateBlobName);

            // Upload content to Azure Blob Storage
            ByteArrayInputStream inputStream = new ByteArrayInputStream(newContent.getBytes(StandardCharsets.UTF_8));

            BlobHttpHeaders headers = new BlobHttpHeaders()
                    .setContentType("text/html; charset=utf-8");

            blobClient.upload(inputStream, newContent.length(), true);
            blobClient.setHttpHeaders(headers);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Template đã được cập nhật thành công trong Azure Blob Storage");
            result.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            result.put("blobName", templateBlobName);
            result.put("blobUrl", blobClient.getBlobUrl());

            log.info("Template updated in Azure Blob Storage: {}", blobClient.getBlobUrl());
            return result;

        } catch (Exception e) {
            log.error("Error updating template in Azure: {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Có lỗi khi cập nhật template: " + e.getMessage());
            return result;
        }
    }

    /**
     * Fallback template content nếu không tìm thấy trong Azure
     */
    private String getDefaultTemplateContent() {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Default Contract Template</title>
                </head>
                <body>
                    <h1>Default Contract Template</h1>
                    <p>Template not found in Azure Blob Storage. Please upload a template.</p>
                </body>
                </html>
                """;
    }

    /**
     * Backup template hiện tại trước khi update
     */
    public String backupCurrentTemplate() {
        if (blobContainerClient == null) {
            log.warn("BlobContainerClient is null, mock backup");
            return "mock-backup-" + System.currentTimeMillis() + ".html";
        }

        try {
            BlobClient currentBlob = blobContainerClient.getBlobClient(templateBlobName);

            if (!currentBlob.exists()) {
                return "No existing template to backup";
            }

            // Tạo backup với timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupBlobName = "backup_" + timestamp + "_" + templateBlobName;

            BlobClient backupBlob = blobContainerClient.getBlobClient(backupBlobName);

            // Copy current template to backup
            backupBlob.copyFromUrl(currentBlob.getBlobUrl());

            log.info("Template backed up to: {}", backupBlobName);
            return "Backed up to: " + backupBlobName;

        } catch (Exception e) {
            log.error("Error backing up template: {}", e.getMessage());
            return "Backup failed: " + e.getMessage();
        }
    }

    /**
     * Kiểm tra template có tồn tại trong Azure Blob Storage không
     */
    public boolean isTemplateExists() {
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
