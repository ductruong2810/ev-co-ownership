package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.UserDocument;
import com.group8.evcoownership.exception.FileStorageException;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.exception.UnauthorizedException;
import com.group8.evcoownership.repository.UserDocumentRepository;
import com.group8.evcoownership.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class UserDocumentService {

    @Autowired
    private UserDocumentRepository userDocumentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AzureBlobStorageService azureBlobStorageService;

    // ================= UPLOAD BATCH DOCUMENTS (FRONT + BACK) WITH DUPLICATE CHECK =================
    @Transactional
    public Map<String, UserDocument> uploadBatchDocuments(
            String email,
            String documentType,
            MultipartFile frontFile,
            MultipartFile backFile) {

        validateDocumentType(documentType);
        validateImage(frontFile);
        validateImage(backFile);

        // CHECK IF TWO FILES ARE IDENTICAL (only in memory, no DB storage)
        try {
            String frontHash = calculateFileHash(frontFile);
            String backHash = calculateFileHash(backFile);

            if (frontHash.equals(backHash)) {
                log.error("Duplicate files detected: frontHash={}, backHash={}",
                        frontHash.substring(0, 8), backHash.substring(0, 8));
                throw new IllegalArgumentException(
                        "Front and back images must be different. Please choose two different images."
                );
            }

            log.info("Files are different (OK): frontHash={}, backHash={}",
                    frontHash.substring(0, 8), backHash.substring(0, 8));

        } catch (IOException e) {
            log.error("Error calculating file hash: {}", e.getMessage());
            throw new RuntimeException("Unable to process file. Please try again.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Map<String, UserDocument> results = new HashMap<>();

        try {
            // Upload FRONT
            UserDocument frontDoc = uploadSingleSide(user, documentType, "FRONT", frontFile);
            results.put("FRONT", frontDoc);

            // Upload BACK
            UserDocument backDoc = uploadSingleSide(user, documentType, "BACK", backFile);
            results.put("BACK", backDoc);

            log.info("Batch upload completed: userId={}, type={}", user.getUserId(), documentType);

        } catch (Exception e) {
            log.error("Batch upload failed: {}", e.getMessage(), e);

            // Rollback: Delete uploaded files from Azure if any
            results.values().forEach(doc -> {
                try {
                    azureBlobStorageService.deleteFile(doc.getImageUrl());
                    userDocumentRepository.delete(doc);
                } catch (Exception cleanupError) {
                    log.error("Failed to cleanup after failed upload: {}", cleanupError.getMessage());
                }
            });

            throw new RuntimeException("Upload failed. Please try again: " + e.getMessage());
        }

        return results;
    }

    // ================= CALCULATE FILE HASH (MD5) - IN MEMORY ONLY =================
    private String calculateFileHash(MultipartFile file) throws IOException {
        try {
            byte[] fileBytes = file.getBytes();
            String hash = DigestUtils.md5Hex(fileBytes);
            log.debug("Calculated hash for {}: {}", file.getOriginalFilename(), hash);
            return hash;
        } catch (IOException e) {
            log.error("Error reading file bytes: {}", e.getMessage());
            throw new IOException("Unable to read file. Please try again.");
        }
    }

    // ================= HELPER: UPLOAD SINGLE SIDE =================
    private UserDocument uploadSingleSide(User user, String documentType, String side, MultipartFile file) {

        // Xóa document cũ nếu đã tồn tại
        userDocumentRepository
                .findByUserIdAndDocumentTypeAndSide(user.getUserId(), documentType, side)
                .ifPresent(existingDoc -> {
                    azureBlobStorageService.deleteFile(existingDoc.getImageUrl());
                    userDocumentRepository.delete(existingDoc);
                });

        // Upload file lên Azure
        String fileUrl = azureBlobStorageService.uploadFile(file);

        // Tạo document mới (NO fileHash field needed)
        UserDocument document = UserDocument.builder()
                .userId(user.getUserId())
                .documentType(documentType)
                .side(side)
                .imageUrl(fileUrl)
                .status("PENDING")
                .build();

        return userDocumentRepository.save(document);
    }

    // ================= GET ALL MY DOCUMENTS =================
    public List<UserDocument> getMyDocuments(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return userDocumentRepository.findByUserId(user.getUserId());
    }

    // ================= GET DOCUMENTS BY TYPE =================
    public List<UserDocument> getDocumentsByType(String email, String documentType) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));

        return userDocumentRepository.findByUserIdAndDocumentType(user.getUserId(), documentType);
    }

    // ================= DELETE DOCUMENT =================
    public void deleteDocument(String email, Long documentId) {
        // Validate ID
        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException("Invalid document ID");
        }

        // Tìm user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Tìm document
        UserDocument document = userDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Document not found with ID: %d. Please check again", documentId)
                ));

        // Check ownership
        if (!document.getUserId().equals(user.getUserId())) {
            throw new UnauthorizedException("You do not have permission to delete this document");
        }

        // Delete từ Azure Blob Storage
        try {
            azureBlobStorageService.deleteFile(document.getImageUrl());
        } catch (Exception e) {
            log.error("Failed to delete file from Azure: {}", e.getMessage(), e);
            throw new FileStorageException("Unable to delete file from storage. Please try again");
        }

        // Delete từ database
        try {
            userDocumentRepository.delete(document);
            log.info("Document deleted successfully: documentId={}, userId={}", documentId, user.getUserId());
        } catch (Exception e) {
            log.error("Failed to delete document from database: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to delete document information. Please try again");
        }
    }

    // ================= VALIDATION HELPERS =================
    private void validateDocumentType(String documentType) {
        if (!documentType.equals("CITIZEN_ID") && !documentType.equals("DRIVER_LICENSE")) {
            throw new IllegalArgumentException("DocumentType must be CITIZEN_ID or DRIVER_LICENSE");
        }
    }

    private void validateImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("File is too large. Maximum size: 10MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("File must be an image (jpg, png, ...)");
        }
    }
}
