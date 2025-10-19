package com.group8.evcoownership.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
public class AzureBlobStorageService {

    private final BlobContainerClient blobContainerClient;

    // Constructor injection
    public AzureBlobStorageService(BlobContainerClient blobContainerClient) {
        log.info("========================================");
        log.info("Initializing AzureBlobStorageService");
        log.info("BlobContainerClient: {}", blobContainerClient == null ? "NULL" : "NOT NULL");
        log.info("========================================");

        if (blobContainerClient == null) {
            throw new IllegalStateException("BlobContainerClient cannot be null!");
        }

        this.blobContainerClient = blobContainerClient;
        log.info("AzureBlobStorageService initialized successfully!");
    }

    public String uploadFile(MultipartFile file) {
        try {
            String originalFileName = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFileName);
            String blobName = UUID.randomUUID() + "_" + System.currentTimeMillis() + fileExtension;

            log.info("Uploading file: {} as {}", originalFileName, blobName);

            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);

            BlobHttpHeaders headers = new BlobHttpHeaders()
                    .setContentType(file.getContentType());

            blobClient.upload(file.getInputStream(), file.getSize(), true);
            blobClient.setHttpHeaders(headers);

            String fileUrl = blobClient.getBlobUrl();
            log.info("File uploaded to Azure: {}", fileUrl);

            return fileUrl;

        } catch (IOException e) {
            log.error("Failed to upload file to Azure: {}", e.getMessage(), e);
            throw new RuntimeException("Could not upload file to Azure Blob Storage", e);
        }
    }

    public void deleteFile(String fileUrl) {
        try {
            String blobName = extractBlobName(fileUrl);
            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);

            if (blobClient.exists()) {
                blobClient.delete();
                log.info("File deleted from Azure: {}", blobName);
            }
        } catch (Exception e) {
            log.error("Failed to delete file from Azure: {}", e.getMessage(), e);
            throw new RuntimeException("Could not delete file from Azure Blob Storage", e);
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex);
    }

    private String extractBlobName(String fileUrl) {
        return fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
    }
}
