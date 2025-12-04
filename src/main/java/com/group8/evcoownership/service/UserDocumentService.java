package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.DocumentPreviewResponseDTO;
import com.group8.evcoownership.dto.DocumentUploadResponseDTO;
import com.group8.evcoownership.dto.UploadedDocumentDTO;
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
// Service xử lý upload, OCR, xác thực giấy tờ cá nhân (CCCD, GPLX,)
// Hỗ trợ upload batch 2 mặt (front/back) cho 1 document (ví dụ CCCD hai mặt)
// Bao gồm validate, trùng lặp, gọi OCR, bóc tách fields, kiểm tra số giấy tờ, upload cloud và lưu DB
public class UserDocumentService {

    private final UserDocumentRepository userDocumentRepository;
    private final UserRepository userRepository;
    private final CloudflareR2StorageService r2StorageService;
    private final OcrService ocrService;
    private final TransactionTemplate transactionTemplate;

    // ===== Constructor: khởi tạo các bean repo/service/phụ trợ dùng trong nghiệp vụ upload =====
    public UserDocumentService(UserDocumentRepository userDocumentRepository,
                               UserRepository userRepository,
                               CloudflareR2StorageService r2StorageService,
                               OcrService ocrService,
                               TransactionTemplate transactionTemplate) {
        this.userDocumentRepository = userDocumentRepository;
        this.userRepository = userRepository;
        this.r2StorageService = r2StorageService;
        this.ocrService = ocrService;
        this.transactionTemplate = transactionTemplate;
    }

    // ========= upload batch giấy tờ 2 mặt cùng lúc =========
    public CompletableFuture<DocumentUploadResponseDTO> uploadBatchDocuments(
            String email,
            String documentType,
            MultipartFile frontFile,
            MultipartFile backFile,
            UserDocumentInfoDTO editedInfo) {

        // Handle null editedInfo
        if (editedInfo == null) {
            // editedInfo will be extracted from OCR in processUpload
        }

        try {
            // 1. Validate loại tài liệu và từng file ảnh hợp lệ/chưa trùng tên
            validateDocumentType(documentType);
            validateImage(frontFile);
            if (backFile != null && !backFile.isEmpty()) {
                validateImage(backFile);
            }

            // 2. Hash từng ảnh để chống upload trùng hai mặt (front == back)
            String frontHash = calculateFileHash(frontFile);
            String backHash = backFile != null ? calculateFileHash(backFile) : null;

            if (frontHash.equals(backHash)) {
                throw new InvalidDocumentException("Front and back images must be different. Please choose two different images.");
            }

            // 3. Lấy user theo email
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new InvalidDocumentException("User not found"));
            Long userId = user.getUserId();
            long startTime = System.currentTimeMillis();

            // 4. Gọi OCR để extract text mặt trước — bất đồng bộ để tránh block luồng xử lý.
            //    Nếu OCR thất bại (timeout, lỗi dịch vụ), fallback sang dùng editedInfo (nếu có) để vẫn cho upload.
            return ocrService.extractTextFromImage(frontFile)
                    .thenApply(extractedText ->
                            // Dùng TransactionTemplate run processUpload trong transaction
                            transactionTemplate.execute(status -> {
                                try {
                                    return processUpload(
                                            userId,
                                            documentType,
                                            frontFile,
                                            backFile,
                                            extractedText,
                                            startTime,
                                            editedInfo
                                    );
                                } catch (Exception e) {
                                    log.error("Upload failed: {}", e.getMessage(), e);
                                    throw new FileProcessingException("Upload failed: " + e.getMessage());
                                }
                            })
                    )
                    .exceptionally(ex -> {
                        log.error("Async OCR or upload failed, trying fallback with editedInfo if available: {}", ex.getMessage(), ex);
                        return transactionTemplate.execute(status -> {
                            try {
                                // Fallback: không có extractedText, processUpload sẽ dựa trên editedInfo (nếu có)
                                return processUpload(
                                        userId,
                                        documentType,
                                        frontFile,
                                        backFile,
                                        null,
                                        startTime,
                                        editedInfo
                                );
                            } catch (Exception e) {
                                log.error("Fallback upload failed: {}", e.getMessage(), e);
                                throw new FileProcessingException("Upload failed: " + e.getMessage());
                            }
                        });
                    });

        } catch (InvalidDocumentException e) {
            // Nếu lỗi validation đầu vào
            log.warn("Validation failed: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        } catch (IOException e) {
            // Lỗi đọc file
            log.error("File processing error: {}", e.getMessage());
            return CompletableFuture.failedFuture(
                    new FileProcessingException("Unable to process file. Please try again.")
            );
        } catch (Exception e) {
            // Lỗi không lường trước
            log.error("Unexpected error: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(
                    new FileProcessingException("Upload failed: " + e.getMessage())
            );
        }
    }

    // ========= Xử lý batch upload cho 1 giấy tờ (cả 2 mặt) =========
    // 1. Dùng text OCR để xác định loại giấy tờ và bóc tách số giấy tờ, metadata
    // 2. Check loại giấy tờ ở FE và loại detect từ OCR có khớp nhau không
    // 3. Kiểm tra số giấy tờ đã tồn tại/chồng lặp chưa (đảm bảo không có trường hợp trùng lặp giữa các user)
    // 4. Gọi upload file lên cloud cho từng mặt FRONT/BACK
    // 5. Trả về thông tin/tài liệu đã lưu thành công hoặc lỗi phát sinh
    private DocumentUploadResponseDTO processUpload(
            Long userId,
            String documentType,
            MultipartFile frontFile,
            MultipartFile backFile,
            String extractedText,
            long startTime,
            UserDocumentInfoDTO editedInfo) {

        boolean hasOcrText = extractedText != null && !extractedText.trim().isEmpty();

        // Nếu không có text OCR và cũng không có editedInfo từ FE => không có nguồn dữ liệu nào để bóc thông tin
        if (!hasOcrText && editedInfo == null) {
            throw new InvalidDocumentException("Unable to extract text from image");
        }

        // Xác định loại giấy tờ:
        // - Nếu có OCR text: dùng OCR để detect & validate như cũ
        // - Nếu OCR fail nhưng có editedInfo: tin tưởng documentType FE gửi lên
        String detectedType = documentType;

        if (hasOcrText) {
            detectedType = detectDocumentType(extractedText);

            if ("UNKNOWN".equals(detectedType)) {
                throw new InvalidDocumentException(
                        "Unable to identify document type from image. Please ensure the image is clear and shows a valid document."
                );
            }

            // Nếu loại giấy tờ FE gửi lên khác loại nhận dạng từ OCR ⇒ báo lỗi để người dùng upload đúng
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
        }

        // Bóc tách dữ liệu từ text ảnh: số giấy tờ, tên, ngày sinh, v.v.
        // Nếu có editedInfo từ FE thì dùng, nếu không thì extract từ OCR (khi có OCR text)
        UserDocumentInfoDTO documentInfo = editedInfo != null ? editedInfo :
                (documentType.equals("CITIZEN_ID")
                        ? extractCitizenIdInfo(extractedText)
                        : extractDriverLicenseInfo(extractedText));

        // Lấy số giấy tờ vừa bóc được (hoặc từ editedInfo)
        String documentNumber = documentInfo.idNumber();

        // Kiểm tra số giấy tờ đã tồn tại ở user khác chưa
        if (documentNumber != null && !documentNumber.isEmpty()) {
            userDocumentRepository.findByDocumentNumber(documentNumber)
                    .ifPresent(existing -> {
                        if (!existing.getUserId().equals(userId)) {
                            throw new DuplicateDocumentException(
                                    String.format("Document number %s is already registered by another user",
                                            documentNumber)
                            );
                        }
                        // Trùng số nhưng là cùng user upload lại ⇒ update
                        log.info("User re-uploading their own document: {}", documentNumber);
                    });
        }

        // Upload file lên cloud (FRONT + BACK)
        Map<String, UserDocument> uploadedDocs = new HashMap<>();
        uploadedDocs.put("FRONT", uploadSingleSideWithDocNumber(
                userId, documentType, "FRONT", frontFile, documentNumber, documentInfo));

        if (backFile != null && !backFile.isEmpty()) {
            uploadedDocs.put("BACK", uploadSingleSideWithDocNumber(
                    userId, documentType, "BACK", backFile, documentNumber, documentInfo));
        }

        log.info("Document uploaded: userId={}, type={}, number={}",
                userId, documentType, documentNumber);

        // Map UserDocument entities to UploadedDocumentDTO
        Map<String, UploadedDocumentDTO> uploadedDocumentsMap = new HashMap<>();

        UserDocument frontDoc = uploadedDocs.get("FRONT");
        if (frontDoc != null) {
            uploadedDocumentsMap.put("FRONT", UploadedDocumentDTO.builder()
                    .documentId(frontDoc.getDocumentId())
                    .imageUrl(frontDoc.getImageUrl())
                    .status(frontDoc.getStatus())
                    .documentNumber(frontDoc.getDocumentNumber())
                    .build());
        }

        UserDocument backDoc = uploadedDocs.get("BACK");
        if (backDoc != null) {
            uploadedDocumentsMap.put("BACK", UploadedDocumentDTO.builder()
                    .documentId(backDoc.getDocumentId())
                    .imageUrl(backDoc.getImageUrl())
                    .status(backDoc.getStatus())
                    .documentNumber(backDoc.getDocumentNumber())
                    .build());
        }

        // Build response DTO
        long processingTime = System.currentTimeMillis() - startTime;
        return DocumentUploadResponseDTO.builder()
                .success(true)
                .uploadedDocuments(uploadedDocumentsMap)
                .documentInfo(documentInfo)
                .detectedType(detectedType)
                .ocrEnabled(true) // Google Vision enabled
                .processingTime(processingTime + "ms")
                .build();
    }

    // ========= Helper nội bộ cho batch upload =========
    // Được gọi trong uploadBatchDocuments/processUpload để xử lý riêng từng mặt (FRONT, BACK) của giấy tờ
    // Không expose ra ngoài như API độc lập – chỉ dùng khi batch upload cùng lúc nhiều ảnh giấy tờ (2 mặt CCCD/GPLX)
    // Giúp gom logic lưu trữ, kiểm tra, xóa ảnh cũ, upload cloud, cập nhật DB cho từng mặt khi batch uploa
    private UserDocument uploadSingleSideWithDocNumber(
            Long userId,
            String documentType,
            String side,
            MultipartFile file,
            String documentNumber,
            UserDocumentInfoDTO documentInfo) {

        log.info("Uploading: userId={}, type={}, side={}, docNumber={}",
                userId, documentType, side, documentNumber);

        // Check số giấy tờ trùng ở user khác (bảo đảm tính duy nhất)
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

        // Nếu user này đã upload mặt này rồi ⇒ xoá ảnh cũ khỏi R2, update mới luôn
        userDocumentRepository
                .findByUserIdAndDocumentTypeAndSide(userId, documentType, side)
                .ifPresent(oldDoc -> {
                    log.info("Deleting old document: docId={}, side={}",
                            oldDoc.getDocumentId(), oldDoc.getSide());

                    try {
                        r2StorageService.deleteFile(oldDoc.getImageUrl());
                    } catch (Exception e) {
                        log.warn("Failed to delete R2 file: {}", e.getMessage());
                    }

                    userDocumentRepository.delete(oldDoc);
                });

        userDocumentRepository.flush();

        // Upload ảnh lên R2, nhận url
        String fileUrl = r2StorageService.uploadFile(file);

        // Nếu là mặt FRONT mới lưu số giấy tờ
        String savedDocNumber = "FRONT".equals(side)
                ? (documentNumber != null ? documentNumber : "")
                : "";

        // Lưu record document vào db
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

    // ========= Nhận dạng loại giấy tờ từ text OCR =========
    // Trả về: CITIZEN_ID | DRIVER_LICENSE | UNKNOWN
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

    // ========= Bóc thông tin CCCD từ text ảnh =========
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

    // ========= Bóc thông tin GPLX từ text ảnh =========
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

    // ========= Bóc số CCCD từ chuỗi OCR =========
    private String extractIdNumber(String text) {
        Pattern pattern = Pattern.compile("\\b(\\d{12}|\\d{9})\\b");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    // ========= Bóc số GPLX từ chuỗi OCR =========
    private String extractLicenseNumber(String text) {
        Pattern pattern = Pattern.compile("(\\d{8,12})");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    // ========= Bóc đủ tên chứa chữ in hoa/or Unicode dấu =====
    private String extractFullName(String text) {
        Pattern pattern = Pattern.compile("(?:họ và tên|ho va ten|full name|name)[:：]?\\s*([A-ZẮẰẲẴẶĂẤẦẨẪẬÂÁÀÃẢẠĐẾỀỂỄỆÊÉÈẺẼẸÍÌỈĨỊỐỒỔỖỘÔỚỜỞỠỢƠÓÒÕỎỌỨỪỬỮỰƯÚÙỦŨỤÝỲỶỸỴ\\s]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    // ========= Bóc ngày sinh ====
    private String extractDateOfBirth(String text) {
        Pattern pattern = Pattern.compile("(?:ngày sinh|date of birth|dob|sinh)[:：]?\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    // ========= Bóc ngày cấp ====
    private String extractIssueDate(String text) {
        Pattern pattern = Pattern.compile("(?:ngày cấp|issue date|date of issue)[:：]?\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    // ========= Bóc ngày hết hạn ====
    private String extractExpiryDate(String text) {
        Pattern pattern = Pattern.compile("(?:có giá trị đến|valid until|expiry date)[:：]?\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    // ========= Bóc địa chỉ (câu dài > 10 ký tự) ====
    private String extractAddress(String text) {
        Pattern pattern = Pattern.compile("(?:địa chỉ|address|dia chi)[:：]?\\s*([^\\n]{10,})", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    // ========= Tính hash file để anti-up trùng ảnh, detect nhanh checksum ảnh đã gặp =========
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

    // ========= Lấy toàn bộ tài liệu của user =========
    public List<UserDocument> getMyDocuments(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidDocumentException("User not found"));

        return userDocumentRepository.findByUserId(user.getUserId());
    }

    // ========= Lấy giấy tờ theo loại (CCCD/GPLX) =========
    public List<UserDocument> getDocumentsByType(String email, String documentType) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidDocumentException("User không tồn tại"));

        return userDocumentRepository.findByUserIdAndDocumentType(user.getUserId(), documentType);
    }

    // ========= Validate loại document (chỉ cho phép CITIZEN_ID, DRIVER_LICENSE) =========
    private void validateDocumentType(String documentType) {
        if (!documentType.equals("CITIZEN_ID") && !documentType.equals("DRIVER_LICENSE")) {
            throw new InvalidDocumentException("DocumentType must be CITIZEN_ID or DRIVER_LICENSE");
        }
    }

    // ========= Kiểm tra file ảnh hợp lệ (còn dung lượng, đúng định dạng image) =========
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

    // ========= Preview OCR extraction (không lưu vào DB) =========
    public CompletableFuture<DocumentPreviewResponseDTO> previewOcrExtraction(
            String documentType,
            MultipartFile frontFile,
            MultipartFile backFile) {

        long startTime = System.currentTimeMillis();

        try {
            // Validate
            validateDocumentType(documentType);
            validateImage(frontFile);
            if (backFile != null && !backFile.isEmpty()) {
                validateImage(backFile);
            }

            // Extract text từ front file (chủ yếu thông tin ở mặt trước)
            return ocrService.extractTextFromImage(frontFile)
                    .thenApply(extractedText -> {
                        if (extractedText == null || extractedText.trim().isEmpty()) {
                            throw new InvalidDocumentException("Unable to extract text from image");
                        }

                        // Detect document type
                        String detectedType = detectDocumentType(extractedText);
                        boolean ocrEnabled = true; // Google Vision enabled

                        // Extract document info
                        UserDocumentInfoDTO documentInfo = documentType.equals("CITIZEN_ID")
                                ? extractCitizenIdInfo(extractedText)
                                : extractDriverLicenseInfo(extractedText);

                        // Build response
                        long finalProcessingTime = System.currentTimeMillis() - startTime;
                        return DocumentPreviewResponseDTO.builder()
                                .success(true)
                                .processingTime(finalProcessingTime + "ms")
                                .textLength(extractedText.length())
                                .extractedText(extractedText)
                                .isRegistrationDocument(ocrService.isVehicleRegistrationDocument(extractedText))
                                .ocrEnabled(ocrEnabled)
                                .detectedType(detectedType)
                                .documentInfo(documentInfo)
                                .build();
                    })
                    .exceptionally(ex -> {
                        log.error("OCR preview failed: {}", ex.getMessage(), ex);
                        throw new RuntimeException("OCR preview failed: " + ex.getMessage());
                    });

        } catch (InvalidDocumentException e) {
            log.warn("Validation failed: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        } catch (Exception e) {
            log.error("Unexpected error in preview OCR: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(
                    new FileProcessingException("Preview OCR failed: " + e.getMessage())
            );
        }
    }
}
