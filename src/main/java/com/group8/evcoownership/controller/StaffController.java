package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.ReviewDocumentRequestDTO;
import com.group8.evcoownership.dto.UserProfileResponseDTO;
import com.group8.evcoownership.service.StaffService;
import com.group8.evcoownership.util.AuthUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/staff")
@Slf4j
public class StaffController {

    @Autowired
    private StaffService staffService;

    @GetMapping("/users")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<List<UserProfileResponseDTO>> getAllUsers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String documentStatus) {

        log.info("Staff fetching all users - status: {}, documentStatus: {}", status, documentStatus);
        List<UserProfileResponseDTO> users = staffService.getAllUsers(status, documentStatus);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponseDTO> getUserDetail(@PathVariable Long userId) {
        log.info("Staff fetching user detail for userId: {}", userId);
        UserProfileResponseDTO user = staffService.getUserDetail(userId);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/documents/pending")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<List<UserProfileResponseDTO>> getUsersWithPendingDocuments() {
        log.info("Staff fetching users with pending documents");
        List<UserProfileResponseDTO> users = staffService.getUsersWithPendingDocuments();
        return ResponseEntity.ok(users);
    }

    @PostMapping("/documents/review/{documentId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> reviewDocument(
            @PathVariable Long documentId,
            @Valid @RequestBody ReviewDocumentRequestDTO request,
            Authentication authentication) {

        String staffEmail = AuthUtils.getCurrentUserEmail(authentication);
        log.info("Staff {} reviewing document {}", staffEmail, documentId);

        String message = staffService.reviewDocument(documentId, request, staffEmail);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/documents/approve/{documentId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> approveDocument(
            @PathVariable Long documentId,
            Authentication authentication) {

        String staffEmail = AuthUtils.getCurrentUserEmail(authentication);
        log.info("Staff {} approving document {}", staffEmail, documentId);

        String message = staffService.approveDocument(documentId, staffEmail);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/documents/reject/{documentId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> rejectDocument(
            @PathVariable Long documentId,
            @RequestParam String reason,
            Authentication authentication) {

        String staffEmail = AuthUtils.getCurrentUserEmail(authentication);
        log.info("Staff {} rejecting document {}", staffEmail, documentId);

        String message = staffService.rejectDocument(documentId, reason, staffEmail);
        return ResponseEntity.ok(Map.of("message", message));
    }
}
