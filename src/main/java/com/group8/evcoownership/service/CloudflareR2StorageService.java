package com.group8.evcoownership.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class CloudflareR2StorageService {

    private final S3Client s3Client;

    @Value("${cloudflare.r2.bucket:}")
    private String bucketName;

    @Value("${cloudflare.r2.public-url:}")
    private String publicUrl;

    @Value("${cloudflare.r2.enabled:false}")
    private boolean enabled;

    // Allowed file types
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp", "application/pdf"
    );

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    @Autowired
    public CloudflareR2StorageService(S3Client r2S3Client) {
        this.s3Client = r2S3Client;
        log.info("========================================");
        log.info("Initializing CloudflareR2StorageService");
        log.info("R2 Enabled: {}", enabled);
        log.info("Bucket: {}", bucketName);
        log.info("Public URL: {}", publicUrl);
        log.info("========================================");
    }

    public String uploadFile(MultipartFile file) {
        if (!enabled || s3Client == null) {
            throw new IllegalStateException("R2 storage is not enabled or S3Client is not initialized");
        }

        // VALIDATION 1: Check null/empty
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống");
        }

        // VALIDATION 2: Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File không được vượt quá 50MB");
        }

        // VALIDATION 3: Check content type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Chỉ chấp nhận file: JPG, PNG, GIF, WEBP, PDF");
        }

        try {
            String originalFileName = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFileName);
            String objectKey = UUID.randomUUID() + "_" + System.currentTimeMillis() + fileExtension;

            log.info("Uploading file to R2: {} (size: {} bytes)", originalFileName, file.getSize());
            log.info("Object key: {}", objectKey);

            // Build PutObjectRequest
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(contentType)
                    .contentDisposition("inline") // Display in browser instead of download
                    .build();

            // Upload file
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            // Build public URL
            String fileUrl = buildPublicUrl(objectKey);
            log.info("File uploaded successfully to R2: {}", fileUrl);

            // Verify file exists
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            try {
                s3Client.headObject(headRequest);
                log.info("File verified successfully: {}", objectKey);
            } catch (NoSuchKeyException e) {
                log.error("CRITICAL: File not found after upload! Key: {}", objectKey);
                throw new RuntimeException("Upload failed - file verification failed");
            }

            return fileUrl;

        } catch (S3Exception e) {
            log.error("R2 S3 error: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể upload file lên R2: " + e.getMessage());
        } catch (IOException e) {
            log.error("IO error during upload: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi đọc file: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during upload: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi không xác định khi upload file");
        }
    }

    public boolean fileExists(String fileUrl) {
        if (!enabled || s3Client == null) {
            return false;
        }

        try {
            String objectKey = extractObjectKey(fileUrl);
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            s3Client.headObject(headRequest);
            log.debug("File exists check for {}: true", objectKey);
            return true;
        } catch (NoSuchKeyException e) {
            log.debug("File exists check: false");
            return false;
        } catch (Exception e) {
            log.error("Error checking file existence: {}", e.getMessage());
            return false;
        }
    }

    public void deleteFile(String fileUrl) {
        if (!enabled || s3Client == null) {
            log.warn("R2 storage is not enabled. Cannot delete file: {}", fileUrl);
            return;
        }

        try {
            String objectKey = extractObjectKey(fileUrl);
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("File deleted from R2: {}", objectKey);
        } catch (Exception e) {
            log.error("Failed to delete file from R2: {}", e.getMessage(), e);
            throw new RuntimeException("Could not delete file from R2", e);
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return ".jpg"; // default
        }
        return fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
    }

    private String extractObjectKey(String fileUrl) {
        // Extract object key from URL
        // Example: https://pub-xxx.r2.dev/user-documents/123/abc.jpg -> user-documents/123/abc.jpg
        // Or: https://your-domain.com/user-documents/123/abc.jpg -> user-documents/123/abc.jpg
        if (fileUrl.contains(bucketName + "/")) {
            return fileUrl.substring(fileUrl.indexOf(bucketName + "/") + bucketName.length() + 1);
        }
        // Fallback: extract from last part of URL
        return fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
    }

    private String buildPublicUrl(String objectKey) {
        if (publicUrl != null && !publicUrl.isEmpty()) {
            // Remove trailing slash if present
            String baseUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
            return baseUrl + "/" + objectKey;
        }
        // Fallback: build URL from endpoint and bucket
        // This might not work if public URL is not configured
        return "https://" + bucketName + ".r2.dev/" + objectKey;
    }
}
