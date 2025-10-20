package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.UserDocument;
import com.group8.evcoownership.exception.FileStorageException;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.exception.UnauthorizedException;
import com.group8.evcoownership.repository.UserDocumentRepository;
import com.group8.evcoownership.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

    // ================= UPLOAD SINGLE DOCUMENT =================
//    public UserDocument uploadDocument(String email, String documentType, String side, MultipartFile file) {
//
//        validateDocumentType(documentType);
//        validateSide(side);
//        validateImage(file);
//
//        User user = userRepository.findByEmail(email)
//                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));
//
//        // Xóa document cũ nếu đã tồn tại (cùng type và side)
//        userDocumentRepository
//                .findByUserIdAndDocumentTypeAndSide(user.getUserId(), documentType, side)
//                .ifPresent(existingDoc -> {
//                    azureBlobStorageService.deleteFile(existingDoc.getImageUrl());
//                    userDocumentRepository.delete(existingDoc);
//                });
//
//        // Upload file lên Azure
//        String fileUrl = azureBlobStorageService.uploadFile(file);
//
//        // Tạo document mới
//        UserDocument document = UserDocument.builder()
//                .userId(user.getUserId())
//                .documentType(documentType)
//                .side(side)
//                .imageUrl(fileUrl)
//                .status("PENDING")
//                .build();
//
//        UserDocument saved = userDocumentRepository.save(document);
//        log.info("Document uploaded: userId={}, type={}, side={}", user.getUserId(), documentType, side);
//
//        return saved;
//    }

    // ================= UPLOAD BATCH DOCUMENTS (FRONT + BACK) =================
    @Transactional
    public Map<String, UserDocument> uploadBatchDocuments(
            String email,
            String documentType,
            MultipartFile frontFile,
            MultipartFile backFile) {

        validateDocumentType(documentType);
        validateImage(frontFile);
        validateImage(backFile);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));

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

            throw new RuntimeException("Upload thất bại. Vui lòng thử lại: " + e.getMessage());
        }

        return results;
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

        // Tạo document mới
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
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));

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
            throw new IllegalArgumentException("ID tài liệu không hợp lệ");
        }

        // Tìm user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng"));

        // Tìm document
        UserDocument document = userDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Không tìm thấy tài liệu với ID: %d. Vui lòng kiểm tra lại", documentId)
                ));

        // Check ownership
        if (!document.getUserId().equals(user.getUserId())) {
            throw new UnauthorizedException("Bạn không có quyền xóa tài liệu này");
        }

        // Delete từ Azure Blob Storage
        try {
            azureBlobStorageService.deleteFile(document.getImageUrl());
        } catch (Exception e) {
            log.error("Failed to delete file from Azure: {}", e.getMessage(), e);
            throw new FileStorageException("Không thể xóa file từ hệ thống lưu trữ. Vui lòng thử lại");
        }

        // Delete từ database
        try {
            userDocumentRepository.delete(document);
            log.info("Document deleted successfully: documentId={}, userId={}", documentId, user.getUserId());
        } catch (Exception e) {
            log.error("Failed to delete document from database: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể xóa thông tin tài liệu. Vui lòng thử lại");
        }
    }

    // ================= VALIDATION HELPERS =================
    private void validateDocumentType(String documentType) {
        if (!documentType.equals("CITIZEN_ID") && !documentType.equals("DRIVER_LICENSE")) {
            throw new IllegalArgumentException("DocumentType phải là CITIZEN_ID hoặc DRIVER_LICENSE");
        }
    }

    private void validateSide(String side) {
        if (!side.equals("FRONT") && !side.equals("BACK")) {
            throw new IllegalArgumentException("Side phải là FRONT hoặc BACK");
        }
    }

    private void validateImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống");
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("File quá lớn. Kích thước tối đa: 10MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("File phải là ảnh (jpg, png, ...)");
        }
    }
}
