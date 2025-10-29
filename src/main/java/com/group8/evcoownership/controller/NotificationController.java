package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.NotificationDto;
import com.group8.evcoownership.entity.Notification;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.NotificationType;
import com.group8.evcoownership.repository.NotificationRepository;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.service.NotificationOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Quản lý thông báo")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationOrchestrator notificationOrchestrator;

    @GetMapping
    @Operation(summary = "Danh sách thông báo", description = "Lấy danh sách thông báo của người dùng hiện tại")
    public ResponseEntity<List<NotificationDto>> getNotifications(
            @AuthenticationPrincipal String email,
            @RequestParam(required = false) Boolean isRead) {
        User user = getUserByEmail(email);

        List<Notification> notifications = (isRead != null)
                ? notificationRepository.findByUserAndIsReadOrderByCreatedAtDesc(user, isRead)
                : notificationRepository.findByUserOrderByUnreadThenCreatedAtDesc(user);

        List<NotificationDto> notificationDtos = notifications.stream()
                .map(this::convertToDto)
                .toList();

        return ResponseEntity.ok(notificationDtos);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Số thông báo chưa đọc", description = "Lấy số lượng thông báo chưa đọc của người dùng")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@AuthenticationPrincipal String email) {
        User user = getUserByEmail(email);

        long count = notificationRepository.countByUserAndIsRead(user, false);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PutMapping("/{notificationId}/read")
    @Operation(summary = "Đánh dấu đã đọc", description = "Đánh dấu một thông báo cụ thể là đã đọc")
    public ResponseEntity<Void> markAsRead(@PathVariable Long notificationId, @AuthenticationPrincipal String email) {
        User user = getUserByEmail(email);
        Notification notification = getNotificationOrThrow(notificationId);
        if (isNotOwner(notification, user)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        notification.setIsRead(true);
        notificationRepository.save(notification);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/mark-all-read")
    @Operation(summary = "Đánh dấu tất cả đã đọc", description = "Đánh dấu tất cả thông báo của người dùng là đã đọc")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal String email) {
        User user = getUserByEmail(email);
        List<Notification> unreadNotifications = notificationRepository.findByUserAndIsRead(user, false);
        unreadNotifications.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unreadNotifications);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{notificationId}")
    @Operation(summary = "Xóa thông báo", description = "Xóa một thông báo cụ thể")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long notificationId, @AuthenticationPrincipal String email) {
        User user = getUserByEmail(email);
        Notification notification = getNotificationOrThrow(notificationId);
        if (isNotOwner(notification, user)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        notificationRepository.delete(notification);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/test")
    @Operation(summary = "Gửi thông báo test", description = "Gửi thông báo test để kiểm tra hệ thống (chỉ dành cho development)")
    public ResponseEntity<Void> sendTestNotification(@AuthenticationPrincipal String email) {
        User user = getUserByEmail(email);

        notificationOrchestrator.sendComprehensiveNotification(
                user.getUserId(),
                com.group8.evcoownership.enums.NotificationType.SYSTEM_MAINTENANCE,
                "Test Notification",
                "This is a test notification to verify the system is working correctly.",
                Map.of("test", true)
        );

        return ResponseEntity.ok().build();
    }

    private NotificationDto convertToDto(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .userId(notification.getUser().getUserId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .notificationType(convertNotificationType(notification.getNotificationType()))
                .isRead(notification.getIsRead())
                .isDelivered(notification.getIsDelivered())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    private NotificationType convertNotificationType(String typeCode) {
        if (typeCode == null) {
            return com.group8.evcoownership.enums.NotificationType.SYSTEM_MAINTENANCE;
        }
        try {
            return com.group8.evcoownership.enums.NotificationType.fromCode(typeCode);
        } catch (IllegalArgumentException e) {
            // Log the error but don't crash - return a default type
            System.err.println("Warning: Unknown notification type: " + typeCode + ". Using SYSTEM_MAINTENANCE as fallback.");
            return com.group8.evcoownership.enums.NotificationType.SYSTEM_MAINTENANCE;
        }
    }

    // ---- helpers to reduce duplication ----
    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Notification getNotificationOrThrow(Long id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
    }

    private boolean isNotOwner(Notification notification, User user) {
        return !notification.getUser().getUserId().equals(user.getUserId());
    }
}
