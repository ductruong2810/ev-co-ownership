package com.group8.evcoownership.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {

    /**
     * Handle notification subscription
     */
    @MessageMapping("/subscribe")
    @SendTo("/topic/notifications")
    public String handleSubscription(String message) {
        return "Subscribed to notifications: " + message;
    }

    /**
     * Handle user-specific notifications
     */
    @MessageMapping("/user/{userId}/notifications")
    @SendTo("/queue/notifications")
    public String handleUserNotification(String message) {
        return "User notification: " + message;
    }

    /**
     * Handle group notifications
     */
    @MessageMapping("/group/{groupId}/notifications")
    @SendTo("/topic/group/{groupId}/notifications")
    public String handleGroupNotification(String message) {
        return "Group notification: " + message;
    }
}
