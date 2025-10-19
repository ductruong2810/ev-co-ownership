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

    @PostMapping("/upload-batch")
    public ResponseEntity<?> uploadBatchDocuments(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("documentTypes") List<String> documentTypes,
            @RequestParam("sides") List<String> sides,
            Authentication authentication) {

        String email = authentication.getName();
        List<UserDocument> documents = userDocumentService.uploadBatchDocuments(email, files, documentTypes, sides);

        return ResponseEntity.ok(Map.of(
                "message", "Upload batch thành công",
                "uploadedCount", documents.size(),
                "documents", documents
        ));
    }

    @GetMapping
    public ResponseEntity<?> getMyDocuments(Authentication authentication) {
        String email = authentication.getName();
        List<UserDocument> documents = userDocumentService.getMyDocuments(email);
        return ResponseEntity.ok(documents);
    }

    // SỬA: Thêm /type/ prefix để tránh conflict với DELETE /{documentId}
    @GetMapping("/type/{documentType}")
    public ResponseEntity<?> getDocumentsByType(
            @PathVariable String documentType,
            Authentication authentication) {

        String email = authentication.getName();
        List<UserDocument> documents = userDocumentService.getDocumentsByType(email, documentType);
        return ResponseEntity.ok(documents);
    }

    // SỬA: PathVariable từ Long → String, thêm parse manual
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
