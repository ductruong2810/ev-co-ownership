package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.WebSocketNotificationDto;
import com.group8.evcoownership.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Send notification to a specific user
     */
    public void sendToUser(Long userId, NotificationType type, String title, String message) {
        sendToUser(userId, type, title, message, "MEDIUM", null, null);
    }

    /**
     * Send notification to a specific user with additional data
     */
    public void sendToUser(Long userId, NotificationType type, String title, String message,
                           String priority, String actionUrl, Map<String, Object> data) {

        WebSocketNotificationDto notification = WebSocketNotificationDto.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .title(title)
                .message(message)
                .notificationType(type)
                .timestamp(LocalDateTime.now())
                .priority(priority != null ? priority : "MEDIUM")
                .data(data)
                .actionUrl(actionUrl)
                .icon(getIconForType(type))
                .build();

        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/notifications",
                notification
        );
    }

    /**
     * Send notification to all users in a group
     */
    public void sendToGroup(Long groupId, NotificationType type, String title, String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("groupId", groupId);

        WebSocketNotificationDto notification = WebSocketNotificationDto.builder()
                .id(UUID.randomUUID().toString())
                .title(title)
                .message(message)
                .notificationType(type)
                .timestamp(LocalDateTime.now())
                .priority("MEDIUM")
                .data(data)
                .icon(getIconForType(type))
                .build();

        messagingTemplate.convertAndSend("/topic/group/" + groupId + "/notifications", notification);
    }

    /**
     * Send notification to all online users (system-wide)
     */
    public void sendToAll(NotificationType type, String title, String message) {
        WebSocketNotificationDto notification = WebSocketNotificationDto.builder()
                .id(UUID.randomUUID().toString())
                .title(title)
                .message(message)
                .notificationType(type)
                .timestamp(LocalDateTime.now())
                .priority("HIGH")
                .icon(getIconForType(type))
                .build();

        messagingTemplate.convertAndSend("/topic/notifications", notification);
    }

    /**
     * Send booking-related notification
     */
    public void sendBookingNotification(Long userId, NotificationType type, String title, String message, Long bookingId) {
        Map<String, Object> data = new HashMap<>();
        data.put("bookingId", bookingId);

        sendToUser(userId, type, title, message, "HIGH", "/bookings/" + bookingId, data);
    }

    /**
     * Send contract-related notification
     */
    public void sendContractNotification(Long userId, NotificationType type, String title, String message, Long contractId) {
        Map<String, Object> data = new HashMap<>();
        data.put("contractId", contractId);

        sendToUser(userId, type, title, message, "HIGH", "/contracts/" + contractId, data);
    }

    /**
     * Send payment-related notification
     */
    public void sendPaymentNotification(Long userId, NotificationType type, String title, String message, Long paymentId) {
        Map<String, Object> data = new HashMap<>();
        data.put("paymentId", paymentId);

        sendToUser(userId, type, title, message, "HIGH", "/payments/" + paymentId, data);
    }

    /**
     * Get icon for notification type
     */
    private String getIconForType(NotificationType type) {
        return switch (type) {
            case BOOKING_CREATED, BOOKING_CONFLICT, BOOKING_CANCELLED, BOOKING_REMINDER -> "🚗";
            case PAYMENT_SUCCESS, PAYMENT_FAILED, PAYMENT_REMINDER, DEPOSIT_REQUIRED, DEPOSIT_OVERDUE -> "💰";
            case CONTRACT_CREATED, CONTRACT_APPROVAL_PENDING, CONTRACT_APPROVED, CONTRACT_REJECTED, CONTRACT_EXPIRING ->
                    "📄";
            case GROUP_CREATED, GROUP_INVITATION, GROUP_MEMBER_JOINED, GROUP_MEMBER_LEFT, GROUP_STATUS_CHANGED -> "👥";
            case MAINTENANCE_REQUESTED, MAINTENANCE_APPROVED, MAINTENANCE_COMPLETED, MAINTENANCE_OVERDUE -> "🔧";
            case VEHICLE_AVAILABLE, VEHICLE_UNAVAILABLE, VEHICLE_EMERGENCY -> "🚙";
            case FUND_LOW_BALANCE, FUND_CONTRIBUTION_REQUIRED, FUND_EXPENSE_APPROVED -> "🏦";
            case SYSTEM_MAINTENANCE, SECURITY_ALERT, POLICY_UPDATE -> "⚠️";
            default -> "📢";
        };
    }
}
