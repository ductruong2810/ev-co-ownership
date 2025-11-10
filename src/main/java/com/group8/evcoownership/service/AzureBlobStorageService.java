package com.group8.evcoownership.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import com.group8.evcoownership.dto.FileInfoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class AzureBlobStorageService {

    private final BlobContainerClient blobContainerClient;

    // Các loại tệp được phép
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp", "application/pdf"
    );

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    public AzureBlobStorageService(BlobContainerClient blobContainerClient) {
        log.info("========================================");
        log.info("Initializing AzureBlobStorageService");
        log.info("BlobContainerClient: {}", blobContainerClient == null ? "NULL" : "NOT NULL");

        if (blobContainerClient == null) {
            throw new IllegalStateException("BlobContainerClient cannot be null!");
        }

        this.blobContainerClient = blobContainerClient;
        log.info("AzureBlobStorageService initialized successfully!");
        log.info("Container name: {}", blobContainerClient.getBlobContainerName());
        log.info("Container URL: {}", blobContainerClient.getBlobContainerUrl());
        log.info("========================================");
    }

    public String uploadFile(MultipartFile file) {
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
            String blobName = UUID.randomUUID() + "_" + System.currentTimeMillis() + fileExtension;

            log.info(" Uploading file: {} (size: {} bytes)", originalFileName, file.getSize());
            log.info(" Blob name: {}", blobName);

            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);

            // Set headers
            BlobHttpHeaders headers = new BlobHttpHeaders()
                    .setContentType(contentType)
                    .setContentDisposition("inline"); // Hiển thị trong trình duyệt thay vì tải xuống

            //  UPLOAD FILE
            blobClient.upload(file.getInputStream(), file.getSize(), true);
            blobClient.setHttpHeaders(headers);

            String fileUrl = blobClient.getBlobUrl();
            log.info(" File uploaded successfully to: {}", fileUrl);

            //XÁC MINH TỆP TỆP TỒN TẠI NGAY SAU KHI TẢI LÊN
            if (!blobClient.exists()) {
                log.error("CRITICAL: File not found after upload! Blob: {}", blobName);
                throw new RuntimeException("Upload failed - file verification failed");
            }

            log.info("File verified successfully: {}", blobName);
            return fileUrl;

        } catch (BlobStorageException e) {
            log.error("Azure Storage error: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể upload file lên Azure: " + e.getMessage());
        } catch (IOException e) {
            log.error("IO error during upload: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi đọc file: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during upload: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi không xác định khi upload file");
        }
    }

    // METHOD ĐỂ KIỂM TRA TỆP CÓ TỒN TẠI KHÔNG
    public boolean fileExists(String fileUrl) {
        try {
            String blobName = extractBlobName(fileUrl);
            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
            boolean exists = blobClient.exists();

            log.debug("File exists check for {}: {}", blobName, exists);
            return exists;

        } catch (Exception e) {
            log.error("Error checking file existence: {}", e.getMessage());
            return false;
        }
    }

    // DELETE FILE
    public void deleteFile(String fileUrl) {
        try {
            String blobName = extractBlobName(fileUrl);
            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);

            if (blobClient.exists()) {
                blobClient.delete();
                log.info(" File deleted from Azure: {}", blobName);
            } else {
                log.warn("File not found for deletion: {}", blobName);
            }
        } catch (Exception e) {
            log.error("Failed to delete file from Azure: {}", e.getMessage(), e);
            throw new RuntimeException("Could not delete file from Azure Blob Storage", e);
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return ".jpg"; // default
        }
        return fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
    }

    private String extractBlobName(String fileUrl) {
        // Extract blob name from URL
        // Example: https://storage.blob.core.windows.net/images/abc.jpg -> abc.jpg
        return fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
    }

}
