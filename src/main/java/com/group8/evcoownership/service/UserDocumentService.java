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
    private final AzureBlobStorageService azureBlobStorageService; // Service upload/xóa file trên Azure Blob
    private final OcrService ocrService;                           // Service gọi OCR để trích xuất text từ ảnh
    private final TransactionTemplate transactionTemplate;         // Dùng để bọc xử lý trong transaction trong context async

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

    // ========= Uload batch documents (Front + Back + OCR + Check duplicate) =========
    public CompletableFuture<Map<String, Object>> uploadBatchDocuments(
            String email,
            String documentType,
            MultipartFile frontFile,
            MultipartFile backFile) {

        try {
            // 1 Validate loại tài liệu (CITIZEN_ID / DRIVER_LICENSE)
            validateDocumentType(documentType);
            // 2 Validate ảnh mặt trước
            validateImage(frontFile);
            // 3 Validate ảnh mặt sau nếu có
            if (backFile != null && !backFile.isEmpty()) {
                validateImage(backFile);
            }

            // 4 Tính hash file để phát hiện user upload cùng 1 ảnh cho front/back
            String frontHash = calculateFileHash(frontFile);
            String backHash = backFile != null ? calculateFileHash(backFile) : null;

            if (frontHash.equals(backHash)) {
                // Không cho phép front và back là cùng 1 ảnh
                throw new IllegalArgumentException(
                        "Front and back images must be different. Please choose two different images."
                );
            }

            // 5 Lấy thông tin user từ email
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            // Lưu sẵn userId để dùng trong async (tránh vấn đề session/transaction trong lambda)
            Long userId = user.getUserId();
            long startTime = System.currentTimeMillis();

            // 6 Gọi OCR async để đọc text từ ảnh mặt trước
            return ocrService.extractTextFromImage(frontFile)
                    .thenApply(extractedText ->
                            // 7) Sau khi OCR xong, bọc phần xử lý upload + lưu DB trong transaction
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
                                    throw new RuntimeException("Upload failed: " + e.getMessage(), e);
                                }
                            })
                    )
                    .exceptionally(ex -> {
                        // Bắt mọi lỗi trong pipeline async và ném ra dưới dạng RuntimeException
                        log.error("Async upload failed: {}", ex.getMessage(), ex);
                        throw new RuntimeException("Upload failed: " + ex.getMessage(), ex);
                    });

        } catch (IllegalArgumentException e) {
            // Lỗi validate đầu vào (documentType, file, ...) -> trả CompletableFuture lỗi
            log.warn("Validation failed: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        } catch (IOException e) {
            // Lỗi đọc file -> thông báo người dùng thử lại
            log.error("File processing error: {}", e.getMessage());
            return CompletableFuture.failedFuture(
                    new RuntimeException("Unable to process file. Please try again.")
            );
        } catch (Exception e) {
            // Lỗi bất ngờ khác
            log.error("Unexpected error: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(
                    new RuntimeException("Upload failed: " + e.getMessage())
            );
        }
    }

    // ========= Xử Lý upload sau khi có kết quả OCR =========
    // Nhận userId (không truyền entity User để tránh vấn đề detached/transaction)
    private Map<String, Object> processUpload(
            Long userId,
            String documentType,
            MultipartFile frontFile,
            MultipartFile backFile,
            String extractedText,
            long startTime) {

        if (extractedText == null || extractedText.trim().isEmpty()) {
            throw new IllegalArgumentException("Unable to extract text from image");
        }

        // 1 Nhận diện loại giấy tờ thực tế từ text OCR (CCCD hay GPLX)
        String detectedType = detectDocumentType(extractedText);

        if ("UNKNOWN".equals(detectedType)) {
            throw new IllegalArgumentException(
                    "Unable to identify document type from image. Please ensure the image is clear and shows a valid document."
            );
        }

        // 2 Kiểm tra loại tài liệu user chọn có khớp với loại OCR nhận diện không
        if (!detectedType.equals(documentType)) {
            String expectedTypeName = "CITIZEN_ID".equals(documentType)
                    ? "Citizen ID (CCCD)"
                    : "Driver License (GPLX)";
            String detectedTypeName = "CITIZEN_ID".equals(detectedType)
                    ? "Citizen ID (CCCD)"
                    : "Driver License (GPLX)";

            throw new IllegalArgumentException(
                    String.format(
                            "Document type mismatch. You selected %s but the image is %s. Please upload the correct document type.",
                            expectedTypeName, detectedTypeName
                    )
            );
        }

        // 3 Dùng OCR để trích thông tin chi tiết tuỳ theo loại giấy tờ
        UserDocumentInfoDTO documentInfo = documentType.equals("CITIZEN_ID")
                ? extractCitizenIdInfo(extractedText)
                : extractDriverLicenseInfo(extractedText);

        String documentNumber = documentInfo.idNumber();

        // 4 Kiểm tra trùng số giấy tờ với user khác (duy nhất 1 user cho 1 số giấy tờ)
        if (documentNumber != null && !documentNumber.isEmpty()) {
            userDocumentRepository.findByDocumentNumber(documentNumber)
                    .ifPresent(existing -> {
                        if (!existing.getUserId().equals(userId)) {
                            // Số này đã được user khác dùng -> reject
                            throw new IllegalArgumentException(
                                    String.format("Document number %s is already registered by another user",
                                            documentNumber)
                            );
                        }
                        // Nếu cùng userId => cho phép re-upload cập nhật lại giấy tờ
                        log.info("User re-uploading their own document: {}", documentNumber);
                    });
        }

        // 5 Upload file front/back tương ứng, lưu record vào DB
        Map<String, UserDocument> uploadedDocs = new HashMap<>();
        uploadedDocs.put("FRONT", uploadSingleSideWithDocNumber(
                userId, documentType, "FRONT", frontFile, documentNumber, documentInfo));

        if (backFile != null && !backFile.isEmpty()) {
            uploadedDocs.put("BACK", uploadSingleSideWithDocNumber(
                    userId, documentType, "BACK", backFile, documentNumber, documentInfo));
        }

        log.info("Document uploaded: userId={}, type={}, number={}",
                userId, documentType, documentNumber);

        // 6 Trả kết quả tổng hợp cho controller
        return Map.of(
                "success", true,
                "uploadedDocuments", uploadedDocs,
                "documentInfo", documentInfo,
                "detectedType", detectedType,
                "processingTime", (System.currentTimeMillis() - startTime) + "ms"
        );
    }

    // ========= Upload 1 batch (FRONT/BACK) + lưu vào DB =========
    private UserDocument uploadSingleSideWithDocNumber(
            Long userId,
            String documentType,
            String side,
            MultipartFile file,
            String documentNumber,
            UserDocumentInfoDTO documentInfo) {

        log.info("Uploading: userId={}, type={}, side={}, docNumber={}",
                userId, documentType, side, documentNumber);

        // 1 Kiểm tra số giấy tờ có đang thuộc user khác không (phòng trường hợp bị gọi trực tiếp hàm này)
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

        // 2 Tìm và xóa tài liệu cũ cùng loại + cùng side (ví dụ: CITIZEN_ID + FRONT)
        userDocumentRepository
                .findByUserIdAndDocumentTypeAndSide(userId, documentType, side)
                .ifPresent(oldDoc -> {
                    log.info("Deleting old document: docId={}, side={}",
                            oldDoc.getDocumentId(), oldDoc.getSide());

                    // Xóa file cũ trên Azure, nếu lỗi chỉ log warning, không chặn flow
                    try {
                        azureBlobStorageService.deleteFile(oldDoc.getImageUrl());
                    } catch (Exception e) {
                        log.warn("Failed to delete Azure file: {}", e.getMessage());
                    }

                    // Xóa record cũ trong DB
                    userDocumentRepository.delete(oldDoc);
                });

        // 3 Flush DB để đảm bảo xóa xong trước khi tạo bản ghi mới
        userDocumentRepository.flush();

        // 4 Upload file mới lên Azure Blob và lấy URL
        String fileUrl = azureBlobStorageService.uploadFile(file);

        // 5 Chỉ lưu documentNumber cho mặt FRONT, mặt BACK để rỗng
        String savedDocNumber = "FRONT".equals(side)
                ? (documentNumber != null ? documentNumber : "")
                : "";

        // 6 Build entity UserDocument với metadata lấy từ OCR
        UserDocument document = UserDocument.builder()
                .userId(userId)
                .documentType(documentType)
                .side(side)
                .imageUrl(fileUrl)
                .documentNumber(savedDocNumber)
                .status("PENDING") // Mặc định PENDING, staff sẽ duyệt sau

                // FRONT: lưu ngày sinh, ngày cấp, ngày hết hạn (nếu có)
                .dateOfBirth("FRONT".equals(side) && documentInfo.dateOfBirth() != null
                        ? documentInfo.dateOfBirth()
                        : "")
                .issueDate("FRONT".equals(side) && documentInfo.issueDate() != null
                        ? documentInfo.issueDate()
                        : "")
                .expiryDate("FRONT".equals(side) && documentInfo.expiryDate() != null
                        ? documentInfo.expiryDate()
                        : "")

                // BACK: lưu địa chỉ (nếu có)
                .address("BACK".equals(side) && documentInfo.address() != null
                        ? documentInfo.address()
                        : "")
                .build();

        return userDocumentRepository.save(document);
    }

    // ========= OCR helper: nhận diện type document =========
    private String detectDocumentType(String extractedText) {
        if (extractedText == null || extractedText.trim().isEmpty()) {
            return "UNKNOWN";
        }

        String text = extractedText.toLowerCase();

        // Tìm các keyword liên quan tới CCCD
        if (text.contains("căn cước công dân") || text.contains("can cuoc cong dan") ||
                text.contains("citizen identification") || text.contains("cccd")) {
            return "CITIZEN_ID";
        }

        // Tìm các keyword liên quan tới GPLX
        if (text.contains("giấy phép lái xe") || text.contains("giay phep lai xe") ||
                text.contains("driver license") || text.contains("gplx")) {
            return "DRIVER_LICENSE";
        }

        return "UNKNOWN";
    }

    // ========= OCR Helper: Trích thông tin từ CCCD =========
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

    // ========= OCR Helper: Trích thông tin từ GPLX =========
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

    // ========= Các hàm Regex trích thông tin từ text của OCR =========
    private String extractIdNumber(String text) {
        // Số CCCD/CMND: 9 hoặc 12 chữ số
        Pattern pattern = Pattern.compile("\\b(\\d{12}|\\d{9})\\b");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractLicenseNumber(String text) {
        // Số GPLX: từ 8 đến 12 chữ số
        Pattern pattern = Pattern.compile("(\\d{8,12})");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractFullName(String text) {
        // Bắt theo các label: họ và tên / full name / name
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

    // ========= Tính hash file để bắt 2 ảnh duplicate =========
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

    // ========= Lấy ds document của user hiện tại =========
    public List<UserDocument> getMyDocuments(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return userDocumentRepository.findByUserId(user.getUserId());
    }

    // ========= lấy document theo type =========
    public List<UserDocument> getDocumentsByType(String email, String documentType) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));

        return userDocumentRepository.findByUserIdAndDocumentType(user.getUserId(), documentType);
    }

    // ========= Validation helpers =========
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
