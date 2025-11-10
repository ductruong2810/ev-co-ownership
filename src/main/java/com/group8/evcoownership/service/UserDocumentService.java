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

    // ================= UPLOAD BATCH DOCUMENTS WITH OCR & DUPLICATE CHECK =================
    @Transactional
    public CompletableFuture<Map<String, Object>> uploadBatchDocuments(
            String email,
            String documentType,
            MultipartFile frontFile,
            MultipartFile backFile) {

        validateDocumentType(documentType);
        validateImage(frontFile);
        if (backFile != null && !backFile.isEmpty()) {
            validateImage(backFile);
        }

        // Check duplicate files
        try {
            String frontHash = calculateFileHash(frontFile);
            String backHash = backFile != null ? calculateFileHash(backFile) : null;

            if (backHash != null && frontHash.equals(backHash)) {
                throw new IllegalArgumentException(
                        "Front and back images must be different. Please choose two different images."
                );
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to process file. Please try again.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        long startTime = System.currentTimeMillis();

        // Step 1: OCR để lấy document number
        return ocrService.extractTextFromImage(frontFile)
                .thenApply(extractedText -> {
                    Map<String, Object> result = new HashMap<>();

                    try {
                        if (extractedText == null || extractedText.trim().isEmpty()) {
                            throw new IllegalArgumentException("Unable to extract text from image");
                        }

                        // Extract document info
                        UserDocumentInfoDTO documentInfo;
                        if (documentType.equals("CITIZEN_ID")) {
                            documentInfo = extractCitizenIdInfo(extractedText);
                        } else {
                            documentInfo = extractDriverLicenseInfo(extractedText);
                        }

                        String documentNumber = documentInfo.idNumber();

                        // Check if document number already exists
                        if (documentNumber != null && !documentNumber.isEmpty()) {
                            Optional<UserDocument> existingDoc =
                                    userDocumentRepository.findByDocumentNumber(documentNumber);

                            if (existingDoc.isPresent()) {
                                UserDocument existing = existingDoc.get();

                                if (!existing.getUserId().equals(user.getUserId())) {
                                    log.warn("Document number {} already used by userId={}",
                                            documentNumber, existing.getUserId());
                                    throw new IllegalArgumentException(
                                            String.format("Document number %s is already registered by another user",
                                                    documentNumber)
                                    );
                                } else {
                                    log.info("User re-uploading their own document: {}", documentNumber);
                                }
                            }
                        }

                        // Step 2: Upload files
                        Map<String, UserDocument> uploadedDocs = new HashMap<>();

                        UserDocument frontDoc = uploadSingleSideWithDocNumber(
                                user, documentType, "FRONT", frontFile, documentNumber);
                        uploadedDocs.put("FRONT", frontDoc);

                        if (backFile != null && !backFile.isEmpty()) {
                            UserDocument backDoc = uploadSingleSideWithDocNumber(
                                    user, documentType, "BACK", backFile, documentNumber);
                            uploadedDocs.put("BACK", backDoc);
                        }

                        result.put("success", true);
                        result.put("uploadedDocuments", uploadedDocs);
                        result.put("documentInfo", documentInfo);
                        result.put("detectedType", detectDocumentType(extractedText));
                        result.put("processingTime", (System.currentTimeMillis() - startTime) + "ms");

                        log.info("Document uploaded: userId={}, type={}, number={}",
                                user.getUserId(), documentType, documentNumber);

                        return result;

                    } catch (IllegalArgumentException e) {
                        throw e;
                    } catch (Exception e) {
                        log.error("Upload failed: {}", e.getMessage(), e);
                        throw new RuntimeException("Upload failed: " + e.getMessage());
                    }
                });
    }

    // ================= HELPER: UPLOAD WITH DOCUMENT NUMBER =================
    private UserDocument uploadSingleSideWithDocNumber(
            User user, String documentType, String side,
            MultipartFile file, String documentNumber) {

        // Xóa tất cả documents cũ có thể conflict
        List<UserDocument> existingDocs = new ArrayList<>();

        // 1. Tìm document với cùng DocumentNumber (nếu có)
        if (documentNumber != null && !documentNumber.isEmpty()) {
            userDocumentRepository.findByDocumentNumber(documentNumber)
                    .filter(doc -> doc.getUserId().equals(user.getUserId()))
                    .ifPresent(existingDocs::add);
        }

        // 2. Tìm document với cùng userId + documentType + side
        userDocumentRepository
                .findByUserIdAndDocumentTypeAndSide(user.getUserId(), documentType, side)
                .ifPresent(existingDocs::add);

        // 3. Xóa tất cả documents cũ tìm được
        existingDocs.stream()
                .distinct()
                .forEach(existingDoc -> {
                    log.info("Deleting existing document: documentId={}, documentNumber={}, side={}",
                            existingDoc.getDocumentId(), existingDoc.getDocumentNumber(), existingDoc.getSide());
                    azureBlobStorageService.deleteFile(existingDoc.getImageUrl());
                    userDocumentRepository.delete(existingDoc);
                });

        String fileUrl = azureBlobStorageService.uploadFile(file);

        UserDocument document = UserDocument.builder()
                .userId(user.getUserId())
                .documentType(documentType)
                .side(side)
                .imageUrl(fileUrl)
                .documentNumber(documentNumber)
                .status("PENDING")
                .build();

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
