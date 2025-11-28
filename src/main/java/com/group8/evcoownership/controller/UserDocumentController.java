package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.DocumentPreviewResponseDTO;
import com.group8.evcoownership.dto.DocumentUploadResponseDTO;
import com.group8.evcoownership.dto.UserDocumentDTO;
import com.group8.evcoownership.dto.UserDocumentInfoDTO;
import com.group8.evcoownership.entity.UserDocument;
import com.group8.evcoownership.service.UserDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/user/documents")
@Slf4j
@Tag(name = "User Documents", description = "Quản lý tài liệu người dùng")
@PreAuthorize("isAuthenticated()")
public class UserDocumentController {

    @Autowired
    private UserDocumentService userDocumentService; // Chứa toàn bộ logic xử lý tài liệu
    // + OCR + kiểm tra trùng lặp

    // ========= Preview OCR (extract but don't save) =========
    @PostMapping("/preview-ocr")
    @Operation(
            summary = "Preview OCR extraction",
            description = "Extract text and document info from images without saving to database"
    )
    @PreAuthorize("hasAnyRole('CO_OWNER','STAFF','ADMIN','TECHNICIAN')")
    public ResponseEntity<DocumentPreviewResponseDTO> previewOcr(
            @RequestParam("documentType") String documentType,
            @RequestParam("frontFile") MultipartFile frontFile,
            @RequestParam("backFile") MultipartFile backFile,
            Authentication authentication
    ) throws Exception {
        String email = authentication.getName();
        log.info("User {} previewing OCR for document type: {}", email, documentType);

        DocumentPreviewResponseDTO result = userDocumentService
                .previewOcrExtraction(documentType, frontFile, backFile)
                .get();

        return ResponseEntity.ok(result);
    }

    // ========= Upload document (batch front + back + OCR) =========
    @PostMapping("/upload-batch")
    @Operation(
            summary = "Upload tài liệu hàng loạt",
            description = "Upload cả mặt trước và mặt sau của tài liệu cùng lúc với OCR và kiểm tra trùng lặp"
    )
    @PreAuthorize("hasAnyRole('CO_OWNER','STAFF','ADMIN','TECHNICIAN')")
    public ResponseEntity<DocumentUploadResponseDTO> uploadBatchDocuments(
            @RequestParam("documentType") String documentType,            // loại tài liệu: CITIZEN_ID, DRIVER_LICENSE
            @RequestParam("frontFile") MultipartFile frontFile,          // file ảnh mặt trước
            @RequestParam(value = "backFile", required = false) MultipartFile backFile, // file ảnh mặt sau (có thể null)
            @RequestParam(value = "editedIdNumber", required = false) String editedIdNumber,
            @RequestParam(value = "editedFullName", required = false) String editedFullName,
            @RequestParam(value = "editedDateOfBirth", required = false) String editedDateOfBirth,
            @RequestParam(value = "editedIssueDate", required = false) String editedIssueDate,
            @RequestParam(value = "editedExpiryDate", required = false) String editedExpiryDate,
            @RequestParam(value = "editedAddress", required = false) String editedAddress,
            Authentication authentication                                // thông tin user hiện tại (từ JWT)
    ) throws Exception { // ném exception lên GlobalExceptionHandler

        String email = authentication.getName(); // email lấy từ Authentication (đã set bởi JwtAuthenticationFilter)
        log.info("User {} uploading batch documents: {}", email, documentType);

        // Build editedInfo từ request params nếu có
        UserDocumentInfoDTO editedInfo = null;
        if (editedIdNumber != null || editedFullName != null || editedDateOfBirth != null ||
                editedIssueDate != null || editedExpiryDate != null || editedAddress != null) {
            editedInfo = new UserDocumentInfoDTO(
                    editedIdNumber,
                    editedFullName,
                    editedDateOfBirth,
                    editedIssueDate,
                    editedExpiryDate,
                    editedAddress
            );
        }

        // gọi service xử lý:
        // kưu file
        // chạy OCR để đọc thông tin trên giấy tờ
        // kiểm tra trùng lặp với các user khác (cùng số CCCD/GPLX)
        // trả về DocumentUploadResponseDTO
        DocumentUploadResponseDTO result = userDocumentService
                .uploadBatchDocuments(email, documentType, frontFile, backFile, editedInfo)
                .get(); // .get() vì service có thể xử lý async (Future/CompletableFuture), lỗi sẽ ném ra tại đây

        log.info("Upload completed successfully for user {}", email);
        return ResponseEntity.ok(result);
    }


    // ========= Lấy ds document của user hiện tại =========
    @GetMapping
    @Operation(
            summary = "Danh sách tài liệu của tôi",
            description = "Lấy danh sách tất cả tài liệu của người dùng hiện tại"
    )
    @PreAuthorize("hasAnyRole('CO_OWNER','STAFF','ADMIN','TECHNICIAN')")
    public ResponseEntity<List<UserDocumentDTO>> getMyDocuments(Authentication authentication) {
        String email = authentication.getName();
        log.info("User {} fetching all documents", email);

        // Lấy list entity UserDocument của user hiện tại
        List<UserDocument> documents = userDocumentService.getMyDocuments(email);

        // Map entity qua DTO để trả ra ngoài (ẩn bớt field không cần thiết)
        List<UserDocumentDTO> dtos = documents.stream()
                .map(UserDocumentDTO::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }


    // ========= Lấy document theo type (VD: CCCD HOẶC GPLX) =========
    @GetMapping("/type/{documentType}")
    @Operation(
            summary = "Tài liệu theo loại",
            description = "Lấy danh sách tài liệu của người dùng theo loại cụ thể"
    )
    @PreAuthorize("hasAnyRole('CO_OWNER','STAFF','ADMIN','TECHNICIAN')")
    public ResponseEntity<List<UserDocumentDTO>> getDocumentsByType(
            @PathVariable String documentType,        // loại tài liệu cần lọc
            Authentication authentication
    ) {
        String email = authentication.getName();
        log.info("User {} fetching documents by type: {}", email, documentType);

        // Lấy các tài liệu của user theo đúng loại
        List<UserDocument> documents = userDocumentService.getDocumentsByType(email, documentType);

        // Map sang DTO để trả ra ngoài
        List<UserDocumentDTO> dtos = documents.stream()
                .map(UserDocumentDTO::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }
//DELETE
//    @DeleteMapping("/{documentId}")
//    @Operation(summary = "Xóa tài liệu", description = "Xóa một tài liệu cụ thể của người dùng")
//    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
//    public ResponseEntity<?> deleteDocument(
//            @PathVariable Long documentId,
//            Authentication authentication) {
//        String email = authentication.getName();
//
//        try {
//            userDocumentService.deleteDocument(email, documentId);
//            return ResponseEntity.ok(Map.of(
//                    "message", "Document deleted successfully",
//                    "documentId", documentId,
//                    "timestamp", System.currentTimeMillis()
//            ));
//        } catch (IllegalArgumentException e) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
//                    .body(Map.of("error", e.getMessage()));
//        } catch (Exception e) {
//            log.error("Error deleting document: {}", e.getMessage(), e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(Map.of("error", "Failed to delete document"));
//        }
//    }
}
