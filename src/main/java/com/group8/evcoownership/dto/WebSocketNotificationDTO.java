package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketNotificationDTO {
    private String id;
    private Long userId;
    private String title;
    private String message;
    private NotificationType notificationType;
    private LocalDateTime timestamp;
    private String priority;
    private Map<String, Object> data; // Additional data for the notification
    private String actionUrl;
    private String icon; // Icon for the notification
}
