package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.service.StaffService;
import com.group8.evcoownership.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/staff")
@Slf4j
@Tag(name = "Staff", description = "Chức năng dành cho nhân viên và quản trị viên")
@PreAuthorize("isAuthenticated()")
public class StaffController {

    @Autowired
    private StaffService staffService;

    @GetMapping("/users")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Danh sách người dùng", description = "Lấy danh sách tất cả người dùng với khả năng lọc theo trạng thái và tài liệu")
    public ResponseEntity<List<UserProfileResponseDTO>> getAllUsers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String documentStatus) {

        log.info("Staff fetching all users - status: {}, documentStatus: {}", status, documentStatus);
        List<UserProfileResponseDTO> users = staffService.getAllUsers(status, documentStatus);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Chi tiết người dùng", description = "Lấy thông tin chi tiết của một người dùng cụ thể")
    public ResponseEntity<UserProfileResponseDTO> getUserDetail(@PathVariable Long userId) {
        log.info("Staff fetching user detail for userId: {}", userId);
        UserProfileResponseDTO user = staffService.getUserDetail(userId);
        return ResponseEntity.ok(user);
    }

    //11/3/2025
    @GetMapping("/users/{userId}/groups")
    public ResponseEntity<List<GroupBookingDTO>> getGroupsByUserId(@PathVariable Long userId) {
        List<GroupBookingDTO> groups = staffService.getGroupsByUserId(userId);
        return ResponseEntity.ok(groups);
    }

    @GetMapping("/documents/pending")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Tài liệu chờ duyệt", description = "Lấy danh sách người dùng có tài liệu chờ duyệt")
    public ResponseEntity<List<UserProfileResponseDTO>> getUsersWithPendingDocuments() {
        log.info("Staff fetching users with pending documents");
        List<UserProfileResponseDTO> users = staffService.getUsersWithPendingDocuments();
        return ResponseEntity.ok(users);
    }

    @PostMapping("/documents/review/{documentId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Duyệt tài liệu", description = "Nhân viên duyệt tài liệu của người dùng")
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
    @Operation(summary = "Phê duyệt tài liệu", description = "Phê duyệt tài liệu của người dùng")
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
    @Operation(summary = "Từ chối tài liệu", description = "Từ chối tài liệu của người dùng với lý do cụ thể")
    public ResponseEntity<Map<String, String>> rejectDocument(
            @PathVariable Long documentId,
            @RequestParam String reason,
            Authentication authentication) {

        String staffEmail = AuthUtils.getCurrentUserEmail(authentication);
        log.info("Staff {} rejecting document {}", staffEmail, documentId);

        String message = staffService.rejectDocument(documentId, reason, staffEmail);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Xóa người dùng (soft delete)", description = "Đặt trạng thái người dùng thành BANNED để vô hiệu hóa tài khoản an toàn")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long userId) {
        String message = staffService.deleteUser(userId);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @GetMapping("/users/qrcodes")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Lấy QR Code tất cả users", description = "Lấy tất cả QR Code của tất cả users từ các group và booking (mỗi trang tối đa 10 users)")
    public ResponseEntity<Page<UserGroupBookingsResponseDTO>> getAllUsersQRCode(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        // Đảm bảo size không vượt quá 10
        if (size > 10) {
            size = 10;
        }

        log.info("Staff fetching all users QR codes - page: {}, size: {}", page, size);
        Page<UserGroupBookingsResponseDTO> response = staffService.getAllUsersQRCode(page, size);
        return ResponseEntity.ok(response);
    }

}
