package com.group8.evcoownership.controller;

import com.group8.evcoownership.entity.UserDocument;
import com.group8.evcoownership.service.UserDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/documents")
@Slf4j
public class UserDocumentController {

    @Autowired
    private UserDocumentService userDocumentService;

    // ================= UPLOAD SINGLE DOCUMENT =================
    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("documentType") String documentType,
            @RequestParam("side") String side,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        String email = authentication.getName();
        UserDocument document = userDocumentService.uploadDocument(email, documentType, side, file);

        return ResponseEntity.ok(Map.of(
                "message", "Upload thành công",
                "documentId", document.getDocumentId(),
                "imageUrl", document.getImageUrl(),
                "status", document.getStatus()
        ));
    }

    // ================= UPLOAD MULTIPLE DOCUMENTS (2 SIDES AT ONCE) =================
    @PostMapping("/upload-batch")
    public ResponseEntity<?> uploadBatchDocuments(
            @RequestParam("documentType") String documentType,
            @RequestParam("frontFile") MultipartFile frontFile,
            @RequestParam("backFile") MultipartFile backFile,
            Authentication authentication) {

        String email = authentication.getName();
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

    // ================= GET ALL MY DOCUMENTS =================
    @GetMapping
    public ResponseEntity<?> getMyDocuments(Authentication authentication) {
        String email = authentication.getName();
        List<UserDocument> documents = userDocumentService.getMyDocuments(email);
        return ResponseEntity.ok(documents);
    }

    // ================= GET DOCUMENTS BY TYPE =================
    @GetMapping("/type/{documentType}")
    public ResponseEntity<?> getDocumentsByType(
            @PathVariable String documentType,
            Authentication authentication) {

        String email = authentication.getName();
        List<UserDocument> documents = userDocumentService.getDocumentsByType(email, documentType);
        return ResponseEntity.ok(documents);
    }

    // ================= DELETE DOCUMENT =================
    @DeleteMapping("/{documentId}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable String documentId,
            Authentication authentication) {

        try {
            Long id = Long.parseLong(documentId);
            String email = authentication.getName();
            userDocumentService.deleteDocument(email, id);

            return ResponseEntity.ok(Map.of("message", "Xóa tài liệu thành công"));

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    String.format("ID tài liệu '%s' không hợp lệ. Vui lòng nhập số nguyên dương", documentId)
            );
        }
    }
}
