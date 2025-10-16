package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.UserDocumentDTO;
import com.group8.evcoownership.service.UserDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")
public class UserDocumentController {

    private static final Logger logger = LoggerFactory.getLogger(UserDocumentController.class);

    @Autowired
    private UserDocumentService documentService;

    // ==================== USER ENDPOINTS ====================

    /**
     * USER: Upload GPLX (mặt trước + mặt sau)
     * POST /api/documents/driver-license/upload
     */
    @PostMapping("/driver-license/upload")
    public ResponseEntity<List<UserDocumentDTO>> uploadDriverLicense(
            @RequestParam("userId") Long userId,
            @RequestParam("frontImage") MultipartFile frontImage,
            @RequestParam(value = "backImage", required = false) MultipartFile backImage) {

        logger.info("User {} uploading driver license", userId);

        List<UserDocumentDTO> result = documentService.uploadDriverLicense(
                userId, frontImage, backImage);

        return ResponseEntity.ok(result);
    }

    /**
     * USER: Upload CCCD (mặt trước + mặt sau)
     * POST /api/documents/citizen-id/upload
     */
    @PostMapping("/citizen-id/upload")
    public ResponseEntity<List<UserDocumentDTO>> uploadCitizenId(
            @RequestParam("userId") Long userId,
            @RequestParam("frontImage") MultipartFile frontImage,
            @RequestParam(value = "backImage", required = false) MultipartFile backImage) {

        logger.info("User {} uploading citizen ID", userId);

        List<UserDocumentDTO> result = documentService.uploadCitizenId(
                userId, frontImage, backImage);

        return ResponseEntity.ok(result);
    }

    /**
     * USER: Xem tất cả document của mình
     * GET /api/documents/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserDocumentDTO>> getUserDocuments(
            @PathVariable Long userId) {

        List<UserDocumentDTO> documents = documentService.getUserDocuments(userId);
        return ResponseEntity.ok(documents);
    }

    /**
     * USER: Xem GPLX của mình
     * GET /api/documents/user/{userId}/driver-license
     */
    @GetMapping("/user/{userId}/driver-license")
    public ResponseEntity<List<UserDocumentDTO>> getUserDriverLicense(
            @PathVariable Long userId) {

        List<UserDocumentDTO> documents = documentService.getUserDriverLicense(userId);
        return ResponseEntity.ok(documents);
    }

    /**
     * USER: Xem CCCD của mình
     * GET /api/documents/user/{userId}/citizen-id
     */
    @GetMapping("/user/{userId}/citizen-id")
    public ResponseEntity<List<UserDocumentDTO>> getUserCitizenId(
            @PathVariable Long userId) {

        List<UserDocumentDTO> documents = documentService.getUserCitizenId(userId);
        return ResponseEntity.ok(documents);
    }

    /**
     * USER: Xóa 1 document (1 mặt)
     * DELETE /api/documents/{documentId}
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<?> deleteDocument(@PathVariable Long documentId) {
        documentService.deleteDocument(documentId);
        return ResponseEntity.ok(Map.of("message", "Xóa giấy tờ thành công"));
    }

    /**
     * USER: Xóa tất cả GPLX của mình
     * DELETE /api/documents/user/{userId}/driver-license
     */
    @DeleteMapping("/user/{userId}/driver-license")
    public ResponseEntity<?> deleteUserDriverLicense(@PathVariable Long userId) {
        documentService.deleteUserDocumentsByType(userId, "DRIVER_LICENSE");
        return ResponseEntity.ok(Map.of("message", "Xóa GPLX thành công"));
    }

    /**
     * USER: Xóa tất cả CCCD của mình
     * DELETE /api/documents/user/{userId}/citizen-id
     */
    @DeleteMapping("/user/{userId}/citizen-id")
    public ResponseEntity<?> deleteUserCitizenId(@PathVariable Long userId) {
        documentService.deleteUserDocumentsByType(userId, "CITIZEN_ID");
        return ResponseEntity.ok(Map.of("message", "Xóa CCCD thành công"));
    }

    // ==================== STAFF ENDPOINTS ====================

    /**
     * STAFF: Xem tất cả document chờ duyệt
     * GET /api/documents/staff/pending
     */
    @GetMapping("/staff/pending")
    public ResponseEntity<List<UserDocumentDTO>> getPendingDocuments() {
        List<UserDocumentDTO> documents = documentService.getPendingDocuments();
        return ResponseEntity.ok(documents);
    }

    /**
     * STAFF: Xem GPLX chờ duyệt
     * GET /api/documents/staff/driver-license/pending
     */
    @GetMapping("/staff/driver-license/pending")
    public ResponseEntity<List<UserDocumentDTO>> getPendingDriverLicenses() {
        List<UserDocumentDTO> documents = documentService.getPendingDriverLicenses();
        return ResponseEntity.ok(documents);
    }

    /**
     * STAFF: Xem CCCD chờ duyệt
     * GET /api/documents/staff/citizen-id/pending
     */
    @GetMapping("/staff/citizen-id/pending")
    public ResponseEntity<List<UserDocumentDTO>> getPendingCitizenIds() {
        List<UserDocumentDTO> documents = documentService.getPendingCitizenIds();
        return ResponseEntity.ok(documents);
    }

    /**
     * STAFF: Duyệt document
     * PUT /api/documents/staff/{documentId}/approve
     */
    @PutMapping("/staff/{documentId}/approve")
    public ResponseEntity<UserDocumentDTO> approveDocument(
            @PathVariable Long documentId,
            @RequestBody Map<String, Object> request) {

        Long staffUserId = Long.parseLong(request.get("staffUserId").toString());
        String note = (String) request.get("note");

        logger.info("Staff {} approving document {}", staffUserId, documentId);

        UserDocumentDTO result = documentService.approveDocument(documentId, staffUserId, note);

        return ResponseEntity.ok(result);
    }

    /**
     * STAFF: Từ chối document
     * PUT /api/documents/staff/{documentId}/reject
     */
    @PutMapping("/staff/{documentId}/reject")
    public ResponseEntity<UserDocumentDTO> rejectDocument(
            @PathVariable Long documentId,
            @RequestBody Map<String, Object> request) {

        Long staffUserId = Long.parseLong(request.get("staffUserId").toString());
        String reason = (String) request.get("reason");

        logger.info("Staff {} rejecting document {}", staffUserId, documentId);

        UserDocumentDTO result = documentService.rejectDocument(documentId, staffUserId, reason);

        return ResponseEntity.ok(result);
    }

    // ==================== COMMON ENDPOINTS ====================

    /**
     * Xem ảnh document
     * GET /api/documents/image/{documentId}
     */
    @GetMapping("/image/{documentId}")
    public ResponseEntity<Resource> viewDocumentImage(@PathVariable Long documentId) {

        Resource resource = documentService.loadImageAsResource(documentId);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"document-" + documentId + ".jpg\"")
                .body(resource);
    }

    /**
     * Health check
     * GET /api/documents/health
     */
    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "User Document Service",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
