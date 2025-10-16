package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.UserDocumentDTO;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.UserDocument;
import com.group8.evcoownership.exception.ImageValidationException;
import com.group8.evcoownership.repository.UserDocumentRepository;
import com.group8.evcoownership.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserDocumentService {

    private static final Logger logger = LoggerFactory.getLogger(UserDocumentService.class);

    // Constants
    private static final String DOC_TYPE_DRIVER_LICENSE = "DRIVER_LICENSE";
    private static final String DOC_TYPE_CITIZEN_ID = "CITIZEN_ID";
    private static final String SIDE_FRONT = "FRONT";
    private static final String SIDE_BACK = "BACK";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    @Value("${file.upload.dir:uploads/documents}")
    private String uploadDir;

    @Autowired
    private UserDocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * USER: Upload GPLX (cả 2 mặt)
     */
    @Transactional
    public List<UserDocumentDTO> uploadDriverLicense(Long userId,
                                                     MultipartFile frontImage,
                                                     MultipartFile backImage) {

        logger.info("User {} uploading driver license", userId);

        // Kiểm tra user tồn tại
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy user"));

        // Kiểm tra đã có GPLX chưa
        if (documentRepository.existsByUserIdAndDocumentTypeAndSide(
                userId, DOC_TYPE_DRIVER_LICENSE, SIDE_FRONT)) {
            throw new IllegalStateException("Bạn đã có GPLX. Vui lòng xóa GPLX cũ trước.");
        }

        // Validate ảnh
        validateImageFile(frontImage, "mặt trước");
        if (backImage != null && !backImage.isEmpty()) {
            validateImageFile(backImage, "mặt sau");
        }

        // Upload ảnh mặt trước
        UserDocument frontDoc = uploadSingleDocument(userId, DOC_TYPE_DRIVER_LICENSE,
                SIDE_FRONT, frontImage);

        // Upload ảnh mặt sau (nếu có)
        UserDocument backDoc = null;
        if (backImage != null && !backImage.isEmpty()) {
            backDoc = uploadSingleDocument(userId, DOC_TYPE_DRIVER_LICENSE,
                    SIDE_BACK, backImage);
        }

        // Trả về danh sách documents
        List<UserDocumentDTO> result = List.of(UserDocumentDTO.fromEntity(frontDoc));
        if (backDoc != null) {
            result = List.of(
                    UserDocumentDTO.fromEntity(frontDoc),
                    UserDocumentDTO.fromEntity(backDoc)
            );
        }

        logger.info("Driver license uploaded successfully for user {}", userId);
        return result;
    }

    /**
     * USER: Upload CCCD (cả 2 mặt)
     */
    @Transactional
    public List<UserDocumentDTO> uploadCitizenId(Long userId,
                                                 MultipartFile frontImage,
                                                 MultipartFile backImage) {

        logger.info("User {} uploading citizen ID", userId);

        // Kiểm tra user tồn tại
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy user"));

        // Kiểm tra đã có CCCD chưa
        if (documentRepository.existsByUserIdAndDocumentTypeAndSide(
                userId, DOC_TYPE_CITIZEN_ID, SIDE_FRONT)) {
            throw new IllegalStateException("Bạn đã có CCCD. Vui lòng xóa CCCD cũ trước.");
        }

        // Validate ảnh
        validateImageFile(frontImage, "mặt trước");
        if (backImage != null && !backImage.isEmpty()) {
            validateImageFile(backImage, "mặt sau");
        }

        // Upload ảnh mặt trước
        UserDocument frontDoc = uploadSingleDocument(userId, DOC_TYPE_CITIZEN_ID,
                SIDE_FRONT, frontImage);

        // Upload ảnh mặt sau (nếu có)
        UserDocument backDoc = null;
        if (backImage != null && !backImage.isEmpty()) {
            backDoc = uploadSingleDocument(userId, DOC_TYPE_CITIZEN_ID,
                    SIDE_BACK, backImage);
        }

        // Trả về danh sách documents
        List<UserDocumentDTO> result = List.of(UserDocumentDTO.fromEntity(frontDoc));
        if (backDoc != null) {
            result = List.of(
                    UserDocumentDTO.fromEntity(frontDoc),
                    UserDocumentDTO.fromEntity(backDoc)
            );
        }

        logger.info("Citizen ID uploaded successfully for user {}", userId);
        return result;
    }

    /**
     * Upload 1 document (1 mặt)
     */
    private UserDocument uploadSingleDocument(Long userId, String documentType,
                                              String side, MultipartFile image) {

        // Lưu file vào thư mục, nhận về đường dẫn
        String imageUrl = saveImageFile(image, documentType, side);

        // Tạo record trong database, lưu đường dẫn
        UserDocument document = UserDocument.builder()
                .userId(userId)
                .documentType(documentType)
                .side(side)
                .imageUrl(imageUrl)  // ← Lưu đường dẫn vào database
                .status(STATUS_PENDING)
                .build();

        return documentRepository.save(document);
    }

    /**
     * STAFF: Lấy tất cả document chờ duyệt
     */
    public List<UserDocumentDTO> getPendingDocuments() {
        return documentRepository.findByStatus(STATUS_PENDING)
                .stream()
                .map(UserDocumentDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * STAFF: Lấy GPLX chờ duyệt
     */
    public List<UserDocumentDTO> getPendingDriverLicenses() {
        return documentRepository.findByDocumentTypeAndStatus(DOC_TYPE_DRIVER_LICENSE, STATUS_PENDING)
                .stream()
                .map(UserDocumentDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * STAFF: Lấy CCCD chờ duyệt
     */
    public List<UserDocumentDTO> getPendingCitizenIds() {
        return documentRepository.findByDocumentTypeAndStatus(DOC_TYPE_CITIZEN_ID, STATUS_PENDING)
                .stream()
                .map(UserDocumentDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * STAFF: Duyệt document
     */
    @Transactional
    public UserDocumentDTO approveDocument(Long documentId, Long staffUserId, String note) {

        UserDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy document"));

        if (!STATUS_PENDING.equals(document.getStatus())) {
            throw new IllegalStateException("Document đã được xử lý rồi");
        }

        User staff = userRepository.findById(staffUserId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy staff"));

        document.setStatus(STATUS_APPROVED);
        document.setReviewNote(note != null ? note : "Đã duyệt");
        document.setReviewedBy(staff);

        UserDocument updated = documentRepository.save(document);
        logger.info("Document {} approved by staff {}", documentId, staffUserId);

        return UserDocumentDTO.fromEntity(updated);
    }

    /**
     * STAFF: Từ chối document
     */
    @Transactional
    public UserDocumentDTO rejectDocument(Long documentId, Long staffUserId, String reason) {

        UserDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy document"));

        if (!STATUS_PENDING.equals(document.getStatus())) {
            throw new IllegalStateException("Document đã được xử lý rồi");
        }

        User staff = userRepository.findById(staffUserId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy staff"));

        document.setStatus(STATUS_REJECTED);
        document.setReviewNote(reason);
        document.setReviewedBy(staff);

        UserDocument updated = documentRepository.save(document);
        logger.info("Document {} rejected by staff {}", documentId, staffUserId);

        return UserDocumentDTO.fromEntity(updated);
    }

    /**
     * USER: Xem tất cả document của mình
     */
    public List<UserDocumentDTO> getUserDocuments(Long userId) {
        return documentRepository.findByUserId(userId)
                .stream()
                .map(UserDocumentDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * USER: Xem GPLX của mình
     */
    public List<UserDocumentDTO> getUserDriverLicense(Long userId) {
        return documentRepository.findByUserIdAndDocumentType(userId, DOC_TYPE_DRIVER_LICENSE)
                .stream()
                .map(UserDocumentDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * USER: Xem CCCD của mình
     */
    public List<UserDocumentDTO> getUserCitizenId(Long userId) {
        return documentRepository.findByUserIdAndDocumentType(userId, DOC_TYPE_CITIZEN_ID)
                .stream()
                .map(UserDocumentDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Xóa document
     */
    @Transactional
    public void deleteDocument(Long documentId) {
        UserDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy document"));

        // Xóa file ảnh
        deleteImageFile(document.getImageUrl());

        // Xóa record
        documentRepository.delete(document);
        logger.info("Document {} deleted", documentId);
    }

    /**
     * Xóa tất cả document của user theo type
     */
    @Transactional
    public void deleteUserDocumentsByType(Long userId, String documentType) {
        List<UserDocument> documents = documentRepository.findByUserIdAndDocumentType(userId, documentType);

        for (UserDocument doc : documents) {
            deleteImageFile(doc.getImageUrl());
            documentRepository.delete(doc);
        }

        logger.info("Deleted all {} documents for user {}", documentType, userId);
    }

    /**
     * Xem ảnh
     */
    public Resource loadImageAsResource(Long documentId) {
        try {
            UserDocument document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy document"));

            Path filePath = Paths.get(document.getImageUrl()).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new IOException("File không tồn tại");
            }

        } catch (IOException e) {
            throw new RuntimeException("Không thể tải ảnh: " + e.getMessage());
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Lưu file ảnh
     */
    private String saveImageFile(MultipartFile file, String documentType, String side) {
        try {
            // Tạo thư mục nếu chưa có
            Path uploadPath = Paths.get(uploadDir);  // uploadDir = "uploads/documents"
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Tạo tên file unique
            String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
            String fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
            String newFileName = documentType + "_" + side + "_" +
                    UUID.randomUUID().toString() + fileExtension;

            // Lưu file
            Path targetLocation = uploadPath.resolve(newFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // TRẢ VỀ ĐƯỜNG DẪN (để lưu vào database)
            String imageUrl = uploadDir + "/" + newFileName;
            logger.info("File saved: {}", imageUrl);

            return imageUrl;  // ← VD: "uploads/documents/DRIVER_LICENSE_FRONT_abc.jpg"

        } catch (IOException e) {
            throw new RuntimeException("Không thể lưu file: " + e.getMessage());
        }
    }

    /**
     * Xóa file ảnh
     */
    private void deleteImageFile(String imageUrl) {
        try {
            Path filePath = Paths.get(imageUrl).normalize();
            Files.deleteIfExists(filePath);
            logger.info("File deleted: {}", imageUrl);
        } catch (IOException e) {
            logger.error("Error deleting file: {}", e.getMessage());
        }
    }

    /**
     * Validate file ảnh
     */
    private void validateImageFile(MultipartFile file, String side) {
        if (file.isEmpty()) {
            throw new ImageValidationException("File ảnh " + side + " không được để trống");
        }

        // Kiểm tra kích thước (max 5MB)
        long maxSize = 5 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new ImageValidationException(
                    String.format("File ảnh %s vượt quá 5MB (%.2f MB)",
                            side, file.getSize() / (1024.0 * 1024.0))
            );
        }

        // Kiểm tra định dạng
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ImageValidationException("File " + side + " phải là ảnh (jpg, png, jpeg)");
        }

        // Kiểm tra độ phân giải (tối thiểu 640x480)
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new ImageValidationException("Không thể đọc file ảnh " + side);
            }

            if (image.getWidth() < 640 || image.getHeight() < 480) {
                throw new ImageValidationException(
                        String.format("Ảnh %s quá nhỏ (%dx%d). Tối thiểu: 640x480",
                                side, image.getWidth(), image.getHeight())
                );
            }
        } catch (IOException e) {
            throw new ImageValidationException("Lỗi khi kiểm tra ảnh " + side);
        }
    }
}
