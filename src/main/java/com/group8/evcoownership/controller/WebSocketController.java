package com.group8.evcoownership.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
@Tag(name = "WebSocket", description = "WebSocket endpoints cho real-time communication")
public class WebSocketController {

    /**
     * Handle notification subscription
     */
    @MessageMapping("/subscribe")
    @SendTo("/topic/notifications")
    @Operation(summary = "Đăng ký thông báo", description = "Đăng ký nhận thông báo real-time từ hệ thống")
    public String handleSubscription(String message) {
        return "Subscribed to notifications: " + message;
    }

    /**
     * Handle user-specific notifications
     */
    @MessageMapping("/user/{userId}/notifications")
    @SendTo("/queue/notifications")
    @Operation(summary = "Thông báo người dùng", description = "Gửi thông báo real-time đến người dùng cụ thể")
    public String handleUserNotification(String message) {
        return "User notification: " + message;
    }

    /**
     * Handle group notifications
     */
    @MessageMapping("/group/{groupId}/notifications")
    @SendTo("/topic/group/{groupId}/notifications")
    @Operation(summary = "Thông báo nhóm", description = "Gửi thông báo real-time đến tất cả thành viên trong nhóm")
    public String handleGroupNotification(String message) {
        return "Group notification: " + message;
    }
}
