package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponseDTO {

    // User Basic Info
    private Long userId;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String avatarUrl;
    private String roleName;
    private String status;
    private LocalDateTime createdAt;

    // Documents
    private DocumentsDTO documents;

    // Statistics
    private StatisticsDTO statistics;

}
