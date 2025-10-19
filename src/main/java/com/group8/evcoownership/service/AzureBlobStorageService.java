package com.group8.evcoownership.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service  // runtime service; tests may subclass with no-arg
@Slf4j
public class AzureBlobStorageService {

    private BlobContainerClient blobContainerClient;

    public AzureBlobStorageService(BlobContainerClient blobContainerClient) {
        this.blobContainerClient = blobContainerClient;
        log.info("AzureBlobStorageService initialized with BlobContainerClient: {}",
                blobContainerClient != null ? "configured" : "null");
    }

    // For test profile - no BlobContainerClient injection
    public AzureBlobStorageService() {
        log.info("AzureBlobStorageService initialized (test mode)");
    }

    public String uploadFile(MultipartFile file) {
        if (blobContainerClient == null) {
            log.warn("BlobContainerClient is null, returning mock URL for file: {}", file.getOriginalFilename());
            return "https://mock-storage.com/files/" + file.getOriginalFilename();
        }

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
            log.error("Failed to upload file to Azure: {}", e.getMessage());
            throw new RuntimeException("Upload thất bại. Vui lòng thử lại: " + e.getMessage(), e);
        }
    }

    public void deleteFile(String fileUrl) {
        if (blobContainerClient == null) {
            log.warn("BlobContainerClient is null, mock delete: {}", fileUrl);
            return;
        }

        try {
            String blobName = extractBlobName(fileUrl);
            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);

            if (blobClient.exists()) {
                blobClient.delete();
                log.info("File deleted from Azure: {}", blobName);
            }
        } catch (Exception e) {
            log.error("Failed to delete file from Azure: {}", e.getMessage());
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
