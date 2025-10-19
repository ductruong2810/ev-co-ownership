package com.group8.evcoownership.controller;

import com.group8.evcoownership.service.TemplateMigrationService;
import com.group8.evcoownership.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/template")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TemplateController {

    private final TemplateService templateService;
    private final TemplateMigrationService templateMigrationService;

    /**
     * Lấy nội dung template hiện tại từ Azure Blob Storage
     */
    @GetMapping("/content")
    public ResponseEntity<String> getTemplateContent() {
        String content = templateService.getTemplateContent();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);

        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }

    /**
     * Upload template file (API chính)
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadTemplateFile(@RequestParam("file") MultipartFile file) {
        // Validate file
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        // Check file size (max 10MB)
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("File size exceeds maximum limit (10MB)");
        }

        // Check file type
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.contains("html") && !contentType.contains("text"))) {
            throw new IllegalArgumentException("File must be HTML or text format. Content-Type: " + contentType);
        }

        try {
            // Read file content
            String htmlContent = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);

            // Validate HTML content
            if (!htmlContent.contains("<html") && !htmlContent.contains("<!DOCTYPE")) {
                throw new IllegalArgumentException("File is not a valid HTML document");
            }

            // Update template với Azure chunked upload
            Map<String, Object> result = templateService.updateTemplateContentChunked(htmlContent);
            result.put("fileName", file.getOriginalFilename());
            result.put("fileSize", file.getSize());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            throw new RuntimeException("Failed to upload template file: " + e.getMessage(), e);
        }
    }

    /**
     * Migrate template từ file local vào Azure Blob Storage (chỉ dùng một lần)
     */
    @PostMapping("/migrate")
    public ResponseEntity<Map<String, Object>> migrateTemplate() {
        templateMigrationService.migrateTemplateToAzure();
        Map<String, Object> result = Map.of(
                "success", true,
                "message", "Template successfully migrated from local file to Azure Blob Storage"
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Kiểm tra template có tồn tại trong Azure không
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkTemplateExists() {
        boolean exists = templateMigrationService.isTemplateExistsInAzure();
        Map<String, Object> result = Map.of(
                "exists", exists,
                "message", exists ? "Template exists in Azure" : "Template not found in Azure"
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Backup template hiện tại
     */
    @PostMapping("/backup")
    public ResponseEntity<Map<String, Object>> backupTemplate() {
        String backupResult = templateService.backupCurrentTemplate();
        Map<String, Object> result = Map.of(
                "success", true,
                "message", "Template successfully backed up",
                "backupInfo", backupResult
        );
        return ResponseEntity.ok(result);
    }
}