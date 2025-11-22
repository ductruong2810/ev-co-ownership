package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.Contract;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.NotificationType;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationOrchestrator {

    private final NotificationService notificationService;
    private final WebSocketNotificationService webSocketService;
    private final EmailNotificationService emailService;
    private final UserRepository userRepository;

    /**
     * Send a comprehensive notification (In-app + WebSocket + Email)
     */
    public void sendComprehensiveNotification(Long userId, NotificationType type, String title, String message,
                                              Map<String, Object> additionalData) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // 1. In-app notification
        notificationService.sendNotification(user, title, message, type.getCode());

        // 2. WebSocket notification (real-time)
        webSocketService.sendToUser(userId, type, title, message, "HIGH",
                getActionUrl(type, additionalData), additionalData);

        // 3. Email notification (for important events)
        if (shouldSendEmail(type)) {
            sendEmailNotification(user, type, additionalData);
        }
    }

    public Map<String, Object> buildContractEmailData(Contract contract) {
        Map<String, Object> data = new HashMap<>();
        data.put("groupId", contract.getGroup().getGroupId());
        data.put("contractId", contract.getId());
        data.put("groupName", contract.getGroup().getGroupName());
        data.put("startDate", contract.getStartDate());
        data.put("endDate", contract.getEndDate());
        data.put("depositAmount", contract.getRequiredDepositAmount());
        data.put("status", contract.getApprovalStatus());
        data.put("rejectionReason", contract.getRejectionReason());
        return data;
    }

    /**
     * Send group notification to all members
     */
    public void sendGroupNotification(Long groupId, NotificationType type, String title, String message) {
        sendGroupNotification(groupId, type, title, message, Map.of("groupId", groupId));
    }

    public void sendGroupNotification(Long groupId, NotificationType type, String title, String message,
                                      Map<String, Object> data) {
        List<User> groupMembers = userRepository.findUsersByGroupId(groupId);

        // ✅ Luôn tạo bản sao mutable để tránh UnsupportedOperationException
        if (data == null) {
            data = new HashMap<>();
        } else if (!(data instanceof HashMap)) {
            data = new HashMap<>(data);
        }

        data.putIfAbsent("groupId", groupId);

        for (User member : groupMembers) {
            notificationService.sendNotification(member, title, message, type.getCode());

            webSocketService.sendToUser(
                    member.getUserId(),
                    type,
                    title,
                    message,
                    "MEDIUM",
                    getActionUrl(type, data),
                    data
            );

            if (shouldSendEmail(type)) {
                sendEmailNotification(member, type, data);
            }
        }

        webSocketService.sendToGroup(groupId, type, title, message);
    }


    /**
     * Determine if email should be sent for this notification type
     */
    private boolean shouldSendEmail(NotificationType type) {
        return switch (type) {
            case CONTRACT_CREATED, CONTRACT_APPROVED, CONTRACT_REJECTED, CONTRACT_EXPIRING,
                 PAYMENT_SUCCESS, PAYMENT_FAILED, DEPOSIT_REQUIRED, DEPOSIT_OVERDUE,
                 GROUP_INVITATION, MAINTENANCE_REQUESTED, MAINTENANCE_APPROVED, MAINTENANCE_COMPLETED,
                 VEHICLE_EMERGENCY, SYSTEM_MAINTENANCE, SECURITY_ALERT, POLICY_UPDATE -> true;
            default -> false;
        };
    }

    /**
     * Get action URL based on a notification type
     */
    private String getActionUrl(NotificationType type, Map<String, Object> data) {
        return switch (type) {
            case BOOKING_CREATED, BOOKING_CONFLICT, BOOKING_CANCELLED -> "/bookings/" + data.get("bookingId");
            case CONTRACT_CREATED, CONTRACT_APPROVAL_PENDING, CONTRACT_APPROVED, CONTRACT_REJECTED ->
                    "/contracts/" + data.get("contractId");
            case PAYMENT_SUCCESS, PAYMENT_FAILED, PAYMENT_REMINDER -> "/payments/" + data.get("paymentId");
            case GROUP_INVITATION, GROUP_MEMBER_JOINED, GROUP_MEMBER_LEFT -> "/groups/" + data.get("groupId");
            case MAINTENANCE_REQUESTED, MAINTENANCE_APPROVED, MAINTENANCE_COMPLETED ->
                    "/maintenance/" + data.get("maintenanceId");
            case FUND_LOW_BALANCE, FUND_CONTRIBUTION_REQUIRED -> "/funds/" + data.get("groupId");
            default -> "/notifications";
        };
    }

    /**
     * Send email notification
     */
    private void sendEmailNotification(User user, NotificationType type, Map<String, Object> data) {
        try {
            switch (type) {
                case CONTRACT_CREATED, CONTRACT_APPROVAL_PENDING, CONTRACT_APPROVED, CONTRACT_REJECTED,
                     CONTRACT_EXPIRING ->
                        emailService.sendContractNotification(user.getEmail(), user.getFullName(), type, data);
                case PAYMENT_SUCCESS, PAYMENT_FAILED, PAYMENT_REMINDER, DEPOSIT_REQUIRED, DEPOSIT_OVERDUE ->
                        emailService.sendPaymentNotification(user.getEmail(), user.getFullName(), type, data);
                case MAINTENANCE_REQUESTED, MAINTENANCE_APPROVED, MAINTENANCE_COMPLETED, MAINTENANCE_OVERDUE ->
                        emailService.sendMaintenanceNotification(user.getEmail(), user.getFullName(), type, data);
                case GROUP_INVITATION, GROUP_MEMBER_JOINED, GROUP_MEMBER_LEFT, GROUP_STATUS_CHANGED ->
                        emailService.sendGroupInvitation(user.getEmail(), user.getFullName(), data);
                default -> {
                    // Send generic notification email
                }
            }
        } catch (Exception e) {
            // Log error but don't fail the notification process
            System.err.println("Failed to send email notification: " + e.getMessage());
        }
    }
}
