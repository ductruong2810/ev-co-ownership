package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.UserDocumentDTO;
import com.group8.evcoownership.entity.UserDocument;
import com.group8.evcoownership.service.UserDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user/documents")
@Slf4j
public class UserDocumentController {

    @Autowired
    private UserDocumentService userDocumentService;

    // ================= UPLOAD MULTIPLE DOCUMENTS (2 SIDES AT ONCE) =================
    @PostMapping("/upload-batch")
    public ResponseEntity<?> uploadBatchDocuments(
            @RequestParam("documentType") String documentType,
            @RequestParam("frontFile") MultipartFile frontFile,
            @RequestParam("backFile") MultipartFile backFile,
            @AuthenticationPrincipal String email) {
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
    }

    // ================= GET ALL MY DOCUMENTS (TRẢ VỀ DTO) =================
    @GetMapping
    public ResponseEntity<List<UserDocumentDTO>> getMyDocuments(@AuthenticationPrincipal String email) {
        log.info("User {} fetching all documents", email);

        List<UserDocument> documents = userDocumentService.getMyDocuments(email);

        // ← MAP SANG DTO ĐỂ TRÁNH CIRCULAR REFERENCE
        List<UserDocumentDTO> dtos = documents.stream()
                .map(UserDocumentDTO::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    // ================= GET DOCUMENTS BY TYPE (TRẢ VỀ DTO) =================
    @GetMapping("/type/{documentType}")
    public ResponseEntity<List<UserDocumentDTO>> getDocumentsByType(
            @PathVariable String documentType,
            @AuthenticationPrincipal String email) {
        log.info("User {} fetching documents by type: {}", email, documentType);

        List<UserDocument> documents = userDocumentService.getDocumentsByType(email, documentType);

        // ← MAP SANG DTO
        List<UserDocumentDTO> dtos = documents.stream()
                .map(UserDocumentDTO::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    // ================= DELETE DOCUMENT =================
    @DeleteMapping("/{documentId}")
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
            throw new IllegalArgumentException(
                    String.format("ID tài liệu '%s' không hợp lệ. Vui lòng nhập số nguyên dương", documentId)
            );
        }
    }
}
