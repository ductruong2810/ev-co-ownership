package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.UserDocumentInfoDTO;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class UserDocumentService {

    @Autowired
    private UserDocumentRepository userDocumentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AzureBlobStorageService azureBlobStorageService;

    @Autowired
    private OcrService ocrService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // ================= UPLOAD BATCH DOCUMENTS =================
    public CompletableFuture<Map<String, Object>> uploadBatchDocuments(
            String email,
            String documentType,
            MultipartFile frontFile,
            MultipartFile backFile) {

        try {
            validateDocumentType(documentType);
            validateImage(frontFile);
            if (backFile != null && !backFile.isEmpty()) {
                validateImage(backFile);
            }

            String frontHash = calculateFileHash(frontFile);
            String backHash = backFile != null ? calculateFileHash(backFile) : null;

            if (backHash != null && frontHash.equals(backHash)) {
                throw new IllegalArgumentException(
                        "Front and back images must be different. Please choose two different images."
                );
            }

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            // LẤY userId TRƯỚC KHI VÀO ASYNC CONTEXT
            Long userId = user.getUserId();
            long startTime = System.currentTimeMillis();

            return ocrService.extractTextFromImage(frontFile)
                    .thenApply(extractedText ->
                            transactionTemplate.execute(status -> {
                                try {
                                    return processUpload(userId, documentType, frontFile,
                                            backFile, extractedText, startTime);
                                } catch (Exception e) {
                                    log.error("Upload failed: {}", e.getMessage(), e);
                                    throw new RuntimeException("Upload failed: " + e.getMessage(), e);
                                }
                            })
                    )
                    .exceptionally(ex -> {
                        log.error("Async upload failed: {}", ex.getMessage(), ex);
                        throw new RuntimeException("Upload failed: " + ex.getMessage(), ex);
                    });

        } catch (IllegalArgumentException e) {
            log.warn("Validation failed: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        } catch (IOException e) {
            log.error("File processing error: {}", e.getMessage());
            return CompletableFuture.failedFuture(
                    new RuntimeException("Unable to process file. Please try again."));
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(
                    new RuntimeException("Upload failed: " + e.getMessage()));
        }
    }

    // ================= PROCESS UPLOAD - NHẬN userId THAY VÌ User =================
    private Map<String, Object> processUpload(
            Long userId, String documentType, MultipartFile frontFile,
            MultipartFile backFile, String extractedText, long startTime) {

        if (extractedText == null || extractedText.trim().isEmpty()) {
            throw new IllegalArgumentException("Unable to extract text from image");
        }

        UserDocumentInfoDTO documentInfo = documentType.equals("CITIZEN_ID")
                ? extractCitizenIdInfo(extractedText)
                : extractDriverLicenseInfo(extractedText);

        String documentNumber = documentInfo.idNumber();

        // Check duplicate document number
        if (documentNumber != null && !documentNumber.isEmpty()) {
            userDocumentRepository.findByDocumentNumber(documentNumber)
                    .ifPresent(existing -> {
                        if (!existing.getUserId().equals(userId)) {
                            throw new IllegalArgumentException(
                                    String.format("Document number %s is already registered by another user",
                                            documentNumber)
                            );
                        }
                        log.info("User re-uploading their own document: {}", documentNumber);
                    });
        }

        // Upload files - TRUYỀN userId THAY VÌ User
        Map<String, UserDocument> uploadedDocs = new HashMap<>();
        uploadedDocs.put("FRONT", uploadSingleSideWithDocNumber(
                userId, documentType, "FRONT", frontFile, documentNumber, documentInfo));

        if (backFile != null && !backFile.isEmpty()) {
            uploadedDocs.put("BACK", uploadSingleSideWithDocNumber(
                    userId, documentType, "BACK", backFile, documentNumber, documentInfo));
        }

        log.info("Document uploaded: userId={}, type={}, number={}",
                userId, documentType, documentNumber);

        return Map.of(
                "success", true,
                "uploadedDocuments", uploadedDocs,
                "documentInfo", documentInfo,
                "detectedType", detectDocumentType(extractedText),
                "processingTime", (System.currentTimeMillis() - startTime) + "ms"
        );
    }

    // ================= UPLOAD SINGLE SIDE - NHẬN userId THAY VÌ User =================
    private UserDocument uploadSingleSideWithDocNumber(
            Long userId, String documentType, String side,
            MultipartFile file, String documentNumber, UserDocumentInfoDTO documentInfo) {
        log.info("📄 Uploading: userId={}, type={}, side={}, docNumber={}",
                userId, documentType, side, documentNumber);
        // 1. Check xem documentNumber có thuộc user khác không
        if (documentNumber != null && !documentNumber.isEmpty()) {
            Optional<UserDocument> otherUserDoc = userDocumentRepository
                    .findByDocumentNumber(documentNumber)
                    .filter(doc -> !doc.getUserId().equals(userId));

            if (otherUserDoc.isPresent()) {
                throw new IllegalArgumentException(
                        String.format("Document number %s is already registered by another user",
                                documentNumber)
                );
            }
        }

        // 2. Xóa TẤT CẢ documents cũ của user này với documentNumber hoặc (documentType + side)
        List<UserDocument> toDelete = new ArrayList<>();

        // Xóa theo documentNumber (nếu có)
        if (documentNumber != null && !documentNumber.isEmpty()) {
            toDelete.addAll(userDocumentRepository
                    .findByUserIdAndDocumentNumber(userId, documentNumber));
        }

        // Xóa theo documentType + side
        userDocumentRepository
                .findByUserIdAndDocumentTypeAndSide(userId, documentType, side)
                .ifPresent(toDelete::add);

        // Xóa duplicate entries
        toDelete.stream()
                .distinct()
                .forEach(doc -> {
                    log.info("Deleting: docId={}, docNumber={}, side={}",
                            doc.getDocumentId(), doc.getDocumentNumber(), doc.getSide());

                    try {
                        azureBlobStorageService.deleteFile(doc.getImageUrl());
                    } catch (Exception e) {
                        log.warn("Failed to delete Azure file: {}", e.getMessage());
                    }

                    userDocumentRepository.delete(doc);
                });

        // 3. Flush để đảm bảo xóa hoàn tất trước khi insert
        userDocumentRepository.flush();

        // 4. Upload file mới
        String fileUrl = azureBlobStorageService.uploadFile(file);

        UserDocument document = UserDocument.builder()
                .userId(userId)
                .documentType(documentType)
                .side(side)
                .imageUrl(fileUrl)
                .documentNumber(documentNumber)
                .status("PENDING")
                .dateOfBirth(documentInfo.dateOfBirth())
                .issueDate(documentInfo.issueDate())
                .expiryDate(documentInfo.expiryDate())
                .address(documentInfo.address())
                .build();

        log.info("💾 Saving: docId={}, side={}, docNumber={}, dob={}, issue={}, expiry={}, address={}",
                document.getDocumentId(), document.getSide(), document.getDocumentNumber(),
                document.getDateOfBirth(), document.getIssueDate(),
                document.getExpiryDate(), document.getAddress());

        return userDocumentRepository.save(document);
    }

    // ================= OCR HELPER METHODS =================

    private String detectDocumentType(String extractedText) {
        if (extractedText == null || extractedText.trim().isEmpty()) {
            return "UNKNOWN";
        }

        String text = extractedText.toLowerCase();

        if (text.contains("căn cước công dân") || text.contains("can cuoc cong dan") ||
                text.contains("citizen identification") || text.contains("cccd")) {
            return "CITIZEN_ID";
        }

        if (text.contains("giấy phép lái xe") || text.contains("giay phep lai xe") ||
                text.contains("driver license") || text.contains("gplx")) {
            return "DRIVER_LICENSE";
        }

        return "UNKNOWN";
    }

    private UserDocumentInfoDTO extractCitizenIdInfo(String extractedText) {
        String idNumber = extractIdNumber(extractedText);
        String fullName = extractFullName(extractedText);
        String dateOfBirth = extractDateOfBirth(extractedText);
        String issueDate = extractIssueDate(extractedText);
        String expiryDate = extractExpiryDate(extractedText);
        String address = extractAddress(extractedText);

        log.info("Extracted Citizen ID - Number: {}, Name: {}", idNumber, fullName);

        return new UserDocumentInfoDTO(idNumber, fullName, dateOfBirth, issueDate, expiryDate, address);
    }

    private UserDocumentInfoDTO extractDriverLicenseInfo(String extractedText) {
        String idNumber = extractLicenseNumber(extractedText);
        String fullName = extractFullName(extractedText);
        String dateOfBirth = extractDateOfBirth(extractedText);
        String issueDate = extractIssueDate(extractedText);
        String expiryDate = extractExpiryDate(extractedText);
        String address = extractAddress(extractedText);

        log.info("Extracted Driver License - Number: {}, Name: {}", idNumber, fullName);

        return new UserDocumentInfoDTO(idNumber, fullName, dateOfBirth, issueDate, expiryDate, address);
    }

    private String extractIdNumber(String text) {
        Pattern pattern = Pattern.compile("\\b(\\d{12}|\\d{9})\\b");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractLicenseNumber(String text) {
        Pattern pattern = Pattern.compile("(\\d{8,12})");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractFullName(String text) {
        Pattern pattern = Pattern.compile("(?:họ và tên|ho va ten|full name|name)[:：]?\\s*([A-ZẮẰẲẴẶĂẤẦẨẪẬÂÁÀÃẢẠĐẾỀỂỄỆÊÉÈẺẼẸÍÌỈĨỊỐỒỔỖỘÔỚỜỞỠỢƠÓÒÕỎỌỨỪỬỮỰƯÚÙỦŨỤÝỲỶỸỴ\\s]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String extractDateOfBirth(String text) {
        Pattern pattern = Pattern.compile("(?:ngày sinh|date of birth|dob|sinh)[:：]?\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractIssueDate(String text) {
        Pattern pattern = Pattern.compile("(?:ngày cấp|issue date|date of issue)[:：]?\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractExpiryDate(String text) {
        Pattern pattern = Pattern.compile("(?:có giá trị đến|valid until|expiry date)[:：]?\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractAddress(String text) {
        Pattern pattern = Pattern.compile("(?:địa chỉ|address|dia chi)[:：]?\\s*([^\\n]{10,})", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    // ================= CALCULATE FILE HASH =================
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
        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException("Invalid document ID");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserDocument document = userDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Document not found with ID: %d. Please check again", documentId)
                ));

        if (!document.getUserId().equals(user.getUserId())) {
            throw new UnauthorizedException("You do not have permission to delete this document");
        }

        try {
            azureBlobStorageService.deleteFile(document.getImageUrl());
        } catch (Exception e) {
            log.error("Failed to delete file from Azure: {}", e.getMessage(), e);
            throw new FileStorageException("Unable to delete file from storage. Please try again");
        }

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
