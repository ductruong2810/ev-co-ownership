package com.group8.evcoownership.dto;

import jakarta.validation.constraints.Size;

/**
 * DTO for admin feedback action (approve/reject) requests
 */
public record FeedbackActionRequestDTO(
        @Size(max = 1000, message = "Admin note must not exceed 1000 characters")
        String adminNote // Optional note from admin
) {
}

