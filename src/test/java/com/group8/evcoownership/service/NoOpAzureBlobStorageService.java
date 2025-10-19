package com.group8.evcoownership.service;

import com.azure.storage.blob.BlobContainerClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("test")
public class NoOpAzureBlobStorageService extends AzureBlobStorageService {
    
    public NoOpAzureBlobStorageService(BlobContainerClient blobContainerClient) {
        super(blobContainerClient);
    }

    @Override
    public String uploadFile(MultipartFile file) {
        return "https://mock-storage.com/files/" + file.getOriginalFilename();
    }

    @Override
    public void deleteFile(String fileUrl) {
        // no-op in tests
    }
}


