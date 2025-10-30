package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private Long id;
    private Long userId;
    private String title;
    private String message;
    private NotificationType notificationType;
    private Boolean isRead;
    private Boolean isDelivered;
    private LocalDateTime createdAt;

    // Additional fields for specific notification types
    private Long relatedEntityId; // ID of related entity (booking, contract, etc.)
    private String relatedEntityType; // Type of related entity
    private String actionUrl; // URL for action button
    private String priority; // HIGH, MEDIUM, LOW
}
