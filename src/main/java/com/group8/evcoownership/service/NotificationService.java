package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.Notification;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.NotificationType;
import com.group8.evcoownership.repository.NotificationRepository;
import com.group8.evcoownership.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // Gửi notification cho một user
    public Notification sendNotification(User user, String title, String message, NotificationType type) {
        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .notificationType(type)
                .isRead(false)
                .isDelivered(false)
                .build();

        return notificationRepository.save(notification);
    }

    // Gửi notification cho nhiều users
    public List<Notification> sendNotificationToUsers(List<User> users, String title, String message, NotificationType type) {
        return users.stream()
                .map(user -> sendNotification(user, title, message, type))
                .toList();
    }

    // Gửi notification cho tất cả users trong ownership group
    public List<Notification> sendNotificationToGroup(List<User> groupUsers, String title, String message, NotificationType type) {
        return sendNotificationToUsers(groupUsers, title, message, type);
    }

    // Gửi notification cho technicians (placeholder - cần implement logic lấy technicians)
    public void sendNotificationToTechnicians(String title, String message) {
        // TODO: Implement logic to get all technicians
        // For now, this is a placeholder method
        // In real implementation, you would:
        // 1. Query users with technician role
        // 2. Send notification to each technician

        // Placeholder implementation - get all users (in real app, filter by role)
        List<User> technicians = userRepository.findAll(); // TODO: Add role-based filtering

        sendNotificationToUsers(technicians, title, message, NotificationType.maintenance);
    }
}
