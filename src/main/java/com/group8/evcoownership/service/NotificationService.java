package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.Notification;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.RoleName;
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
    public void sendNotification(User user, String title, String message, String type) {
        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .notificationType(type)
                .isRead(false)
                .isDelivered(false)
                .build();

        notificationRepository.save(notification);
    }

    // Gửi notification cho nhiều users
    public void sendNotificationToUsers(List<User> users, String title, String message, String type) {
        users.forEach(user -> sendNotification(user, title, message, type));
    }

    // Gửi notification cho tất cả users trong ownership group
    public void sendNotificationToGroup(List<User> groupUsers, String title, String message, String type) {
        sendNotificationToUsers(groupUsers, title, message, type);
    }

    // Gửi notification cho technicians
    public void sendNotificationToTechnicians(String title, String message) {
        // Query users with a technician role
        List<User> technicians = userRepository.findByRoleRoleName(RoleName.TECHNICIAN);

        if (!technicians.isEmpty()) {
            sendNotificationToUsers(technicians, title, message, "MAINTENANCE");
        }
    }
}
