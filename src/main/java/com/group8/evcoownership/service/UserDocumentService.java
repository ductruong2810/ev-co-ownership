package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.UserDocumentInfoDTO;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.UserDocument;
import com.group8.evcoownership.exception.DuplicateDocumentException;
import com.group8.evcoownership.exception.FileProcessingException;
import com.group8.evcoownership.exception.InvalidDocumentException;
import com.group8.evcoownership.repository.UserDocumentRepository;
import com.group8.evcoownership.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@Transactional
public class UserDocumentService {

    private final UserDocumentRepository userDocumentRepository;
    private final UserRepository userRepository;
    private final AzureBlobStorageService azureBlobStorageService;
    private final OcrService ocrService;
    private final TransactionTemplate transactionTemplate;

    public UserDocumentService(UserDocumentRepository userDocumentRepository,
                               UserRepository userRepository,
                               AzureBlobStorageService azureBlobStorageService,
                               OcrService ocrService,
                               TransactionTemplate transactionTemplate) {
        this.userDocumentRepository = userDocumentRepository;
        this.userRepository = userRepository;
        this.azureBlobStorageService = azureBlobStorageService;
        this.ocrService = ocrService;
        this.transactionTemplate = transactionTemplate;
    }

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

            if (frontHash.equals(backHash)) {
                throw new InvalidDocumentException("Front and back images must be different. Please choose two different images.");
            }

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new InvalidDocumentException("User not found"));

            Long userId = user.getUserId();
            long startTime = System.currentTimeMillis();

            return ocrService.extractTextFromImage(frontFile)
                    .thenApply(extractedText ->
                            transactionTemplate.execute(status -> {
                                try {
                                    return processUpload(
                                            userId,
                                            documentType,
                                            frontFile,
                                            backFile,
                                            extractedText,
                                            startTime
                                    );
                                } catch (Exception e) {
                                    log.error("Upload failed: {}", e.getMessage(), e);
                                    throw new FileProcessingException("Upload failed: " + e.getMessage());
                                }
                            })
                    )
                    .exceptionally(ex -> {
                        log.error("Async upload failed: {}", ex.getMessage(), ex);
                        throw new FileProcessingException("Upload failed: " + ex.getMessage());
                    });

        } catch (InvalidDocumentException e) {
            log.warn("Validation failed: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        } catch (IOException e) {
            log.error("File processing error: {}", e.getMessage());
            return CompletableFuture.failedFuture(
                    new FileProcessingException("Unable to process file. Please try again.")
            );
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(
                    new FileProcessingException("Upload failed: " + e.getMessage())
            );
        }
    }

    private Map<String, Object> processUpload(
            Long userId,
            String documentType,
            MultipartFile frontFile,
            MultipartFile backFile,
            String extractedText,
            long startTime) {

        if (extractedText == null || extractedText.trim().isEmpty()) {
            throw new InvalidDocumentException("Unable to extract text from image");
        }

        String detectedType = detectDocumentType(extractedText);

        if ("UNKNOWN".equals(detectedType)) {
            throw new InvalidDocumentException(
                    "Unable to identify document type from image. Please ensure the image is clear and shows a valid document."
            );
        }

        if (!detectedType.equals(documentType)) {
            String expectedTypeName = "CITIZEN_ID".equals(documentType)
                    ? "Citizen ID (CCCD)"
                    : "Driver License (GPLX)";
            String detectedTypeName = "CITIZEN_ID".equals(detectedType)
                    ? "Citizen ID (CCCD)"
                    : "Driver License (GPLX)";

            throw new InvalidDocumentException(
                    String.format(
                            "Document type mismatch. You selected %s but the image is %s. Please upload the correct document type.",
                            expectedTypeName, detectedTypeName
                    )
            );
        }

        UserDocumentInfoDTO documentInfo = documentType.equals("CITIZEN_ID")
                ? extractCitizenIdInfo(extractedText)
                : extractDriverLicenseInfo(extractedText);

        String documentNumber = documentInfo.idNumber();

        if (documentNumber != null && !documentNumber.isEmpty()) {
            userDocumentRepository.findByDocumentNumber(documentNumber)
                    .ifPresent(existing -> {
                        if (!existing.getUserId().equals(userId)) {
                            throw new DuplicateDocumentException(
                                    String.format("Document number %s is already registered by another user",
                                            documentNumber)
                            );
                        }
                        log.info("User re-uploading their own document: {}", documentNumber);
                    });
        }

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
                "detectedType", detectedType,
                "processingTime", (System.currentTimeMillis() - startTime) + "ms"
        );
    }

    private UserDocument uploadSingleSideWithDocNumber(
            Long userId,
            String documentType,
            String side,
            MultipartFile file,
            String documentNumber,
            UserDocumentInfoDTO documentInfo) {

        log.info("Uploading: userId={}, type={}, side={}, docNumber={}",
                userId, documentType, side, documentNumber);

        if (documentNumber != null && !documentNumber.isEmpty()) {
            Optional<UserDocument> otherUserDoc = userDocumentRepository
                    .findByDocumentNumber(documentNumber)
                    .filter(doc -> !doc.getUserId().equals(userId));

            if (otherUserDoc.isPresent()) {
                throw new DuplicateDocumentException(
                        String.format("Document number %s is already registered by another user",
                                documentNumber)
                );
            }
        }

        userDocumentRepository
                .findByUserIdAndDocumentTypeAndSide(userId, documentType, side)
                .ifPresent(oldDoc -> {
                    log.info("Deleting old document: docId={}, side={}",
                            oldDoc.getDocumentId(), oldDoc.getSide());

                    try {
                        azureBlobStorageService.deleteFile(oldDoc.getImageUrl());
                    } catch (Exception e) {
                        log.warn("Failed to delete Azure file: {}", e.getMessage());
                    }

                    userDocumentRepository.delete(oldDoc);
                });

        userDocumentRepository.flush();

        String fileUrl = azureBlobStorageService.uploadFile(file);

        String savedDocNumber = "FRONT".equals(side)
                ? (documentNumber != null ? documentNumber : "")
                : "";

        UserDocument document = UserDocument.builder()
                .userId(userId)
                .documentType(documentType)
                .side(side)
                .imageUrl(fileUrl)
                .documentNumber(savedDocNumber)
                .status("PENDING")
                .dateOfBirth("FRONT".equals(side) && documentInfo.dateOfBirth() != null
                        ? documentInfo.dateOfBirth()
                        : "")
                .issueDate("FRONT".equals(side) && documentInfo.issueDate() != null
                        ? documentInfo.issueDate()
                        : "")
                .expiryDate("FRONT".equals(side) && documentInfo.expiryDate() != null
                        ? documentInfo.expiryDate()
                        : "")
                .address("BACK".equals(side) && documentInfo.address() != null
                        ? documentInfo.address()
                        : "")
                .build();

        return userDocumentRepository.save(document);
    }

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

    public List<UserDocument> getMyDocuments(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidDocumentException("User not found"));

        return userDocumentRepository.findByUserId(user.getUserId());
    }

    public List<UserDocument> getDocumentsByType(String email, String documentType) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidDocumentException("User không tồn tại"));

        return userDocumentRepository.findByUserIdAndDocumentType(user.getUserId(), documentType);
    }

    private void validateDocumentType(String documentType) {
        if (!documentType.equals("CITIZEN_ID") && !documentType.equals("DRIVER_LICENSE")) {
            throw new InvalidDocumentException("DocumentType must be CITIZEN_ID or DRIVER_LICENSE");
        }
    }

    private void validateImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidDocumentException("File must not be empty");
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new InvalidDocumentException("File is too large. Maximum size: 10MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidDocumentException("File must be an image (jpg, png, ...)");
        }
    }
}
