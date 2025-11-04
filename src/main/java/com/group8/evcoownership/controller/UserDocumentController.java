package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.UserDocumentDTO;
import com.group8.evcoownership.entity.UserDocument;
import com.group8.evcoownership.service.UserDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user/documents")
@Slf4j
@Tag(name = "User Documents", description = "Quản lý tài liệu người dùng")
@PreAuthorize("isAuthenticated()")
public class UserDocumentController {

    @Autowired
    private UserDocumentService userDocumentService;

    // ================= UPLOAD MULTIPLE DOCUMENTS (2 SIDES AT ONCE) =================
    @PostMapping("/upload-batch")
    @Operation(summary = "Upload tài liệu hàng loạt", description = "Upload cả mặt trước và mặt sau của tài liệu cùng lúc")
    @PreAuthorize("hasAnyRole('CO_OWNER','STAFF','ADMIN','TECHNICIAN')")
    public ResponseEntity<?> uploadBatchDocuments(
            @RequestParam("documentType") String documentType,
            @RequestParam("frontFile") MultipartFile frontFile,
            @RequestParam("backFile") MultipartFile backFile,
            @AuthenticationPrincipal String email) {

        try {
            log.info("User {} uploading batch documents: {}", email, documentType);

            Map<String, UserDocument> documents = userDocumentService.uploadBatchDocuments(
                    email, documentType, frontFile, backFile
            );

            return ResponseEntity.ok(Map.of(
                    "message", "Upload cả 2 mặt thành công",
                    "front", Map.of(
                            "documentId", documents.get("FRONT").getDocumentId(),
                            "imageUrl", documents.get("FRONT").getImageUrl(),
                            "status", documents.get("FRONT").getStatus()
                    ),
                    "back", Map.of(
                            "documentId", documents.get("BACK").getDocumentId(),
                            "imageUrl", documents.get("BACK").getImageUrl(),
                            "status", documents.get("BACK").getStatus()
                    )
            ));

        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 400,
                            "error", "Bad Request",
                            "message", e.getMessage(),
                            "path", "/api/user/documents/upload-batch"
                    ));

        } catch (Exception e) {
            log.error("Upload error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 500,
                            "error", "Internal Server Error",
                            "message", "Không thể upload file. Vui lòng thử lại sau.",
                            "path", "/api/user/documents/upload-batch"
                    ));
        }
    }

    // ================= GET ALL MY DOCUMENTS (TRẢ VỀ DTO) =================
    @GetMapping
    @Operation(summary = "Danh sách tài liệu của tôi", description = "Lấy danh sách tất cả tài liệu của người dùng hiện tại")
    @PreAuthorize("hasAnyRole('CO_OWNER','STAFF','ADMIN','TECHNICIAN')")
    public ResponseEntity<List<UserDocumentDTO>> getMyDocuments(@AuthenticationPrincipal String email) {
        log.info("User {} fetching all documents", email);

        List<UserDocument> documents = userDocumentService.getMyDocuments(email);

        List<UserDocumentDTO> dtos = documents.stream()
                .map(UserDocumentDTO::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    // ================= GET DOCUMENTS BY TYPE (TRẢ VỀ DTO) =================
    @GetMapping("/type/{documentType}")
    @Operation(summary = "Tài liệu theo loại", description = "Lấy danh sách tài liệu của người dùng theo loại cụ thể")
    @PreAuthorize("hasAnyRole('CO_OWNER','STAFF','ADMIN','TECHNICIAN')")
    public ResponseEntity<List<UserDocumentDTO>> getDocumentsByType(
            @PathVariable String documentType,
            @AuthenticationPrincipal String email) {
        log.info("User {} fetching documents by type: {}", email, documentType);

        List<UserDocument> documents = userDocumentService.getDocumentsByType(email, documentType);

        List<UserDocumentDTO> dtos = documents.stream()
                .map(UserDocumentDTO::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    // ================= DELETE DOCUMENT =================
    @DeleteMapping("/{documentId}")
    @Operation(summary = "Xóa tài liệu", description = "Xóa một tài liệu cụ thể của người dùng")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<?> deleteDocument(
            @PathVariable String documentId,
            @AuthenticationPrincipal String email) {

        try {
            Long id = Long.parseLong(documentId);

            log.info("User {} deleting document: {}", email, id);
            userDocumentService.deleteDocument(email, id);

            return ResponseEntity.ok(Map.of("message", "Xóa tài liệu thành công"));

        } catch (NumberFormatException e) {
            log.error("Invalid document ID format: {}", documentId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 400,
                            "error", "Bad Request",
                            "message", String.format("ID tài liệu '%s' không hợp lệ. Vui lòng nhập số nguyên dương", documentId),
                            "path", "/api/user/documents/" + documentId
                    ));

        } catch (Exception e) {
            log.error("Delete error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 500,
                            "error", "Internal Server Error",
                            "message", e.getMessage(),
                            "path", "/api/user/documents/" + documentId
                    ));
        }
    }
}
