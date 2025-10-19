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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Slf4j
public class UserDocumentService {

    @Autowired
    private UserDocumentRepository userDocumentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AzureBlobStorageService azureBlobStorageService;

    /**
     * Upload document lên Azure Blob Storage
     */
    public UserDocument uploadDocument(String email, String documentType, String side, MultipartFile file) {

        validateDocumentType(documentType);
        validateSide(side);
        validateImage(file);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));


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

        UserDocument saved = userDocumentRepository.save(document);
        log.info("Document uploaded: userId={}, type={}, side={}", user.getUserId(), documentType, side);

        return saved;
    }

    /**
     * Upload multiple documents in batch
     */
    public List<UserDocument> uploadBatchDocuments(String email, List<MultipartFile> files, List<String> documentTypes, List<String> sides) {
        if (files.size() != documentTypes.size() || files.size() != sides.size()) {
            throw new IllegalArgumentException("Số lượng files, documentTypes và sides phải bằng nhau");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));

        List<UserDocument> uploadedDocuments = new java.util.ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            try {
                MultipartFile file = files.get(i);
                String documentType = documentTypes.get(i);
                String side = sides.get(i);

                validateDocumentType(documentType);
                validateSide(side);
                validateImage(file);

                // Delete existing document if any
                userDocumentRepository
                        .findByUserIdAndDocumentTypeAndSide(user.getUserId(), documentType, side)
                        .ifPresent(existingDoc -> {
                            azureBlobStorageService.deleteFile(existingDoc.getImageUrl());
                            userDocumentRepository.delete(existingDoc);
                        });

                // Upload file to Azure
                String fileUrl = azureBlobStorageService.uploadFile(file);

                // Create new document
                UserDocument document = UserDocument.builder()
                        .userId(user.getUserId())
                        .documentType(documentType)
                        .side(side)
                        .imageUrl(fileUrl)
                        .status("PENDING")
                        .build();

                UserDocument saved = userDocumentRepository.save(document);
                uploadedDocuments.add(saved);
                log.info("Document uploaded in batch: userId={}, type={}, side={}", user.getUserId(), documentType, side);

            } catch (Exception e) {
                log.error("Failed to upload document in batch: {}", e.getMessage(), e);
                throw new RuntimeException("Upload thất bại. Vui lòng thử lại: " + e.getMessage(), e);
            }
        }

        return uploadedDocuments;
    }

    /**
     * Lấy tất cả document của user hiện tại
     */
    public List<UserDocument> getMyDocuments(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));

        return userDocumentRepository.findByUserId(user.getUserId());
    }

    /**
     * Lấy document theo type (CCCD hoặc GPLX)
     */
    public List<UserDocument> getDocumentsByType(String email, String documentType) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));

        return userDocumentRepository.findByUserIdAndDocumentType(user.getUserId(), documentType);
    }

    /**
     * Xóa document
     */
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
