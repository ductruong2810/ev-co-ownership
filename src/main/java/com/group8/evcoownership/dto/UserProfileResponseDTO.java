package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponseDTO {

    // Basic Info
    private Long userId;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String avatarUrl;
    private String roleName;
    private String status;
    private LocalDateTime createdAt;

    // Documents Images (Chỉ có ảnh thoi nha:3)
    private DocumentImages documents;

    // Statistics
    private StatisticsDTO statistics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentImages {
        private List<DocumentDTO> citizenIdImages; // CCCD front + back
        private List<DocumentDTO> driverLicenseImages; // GPLX front + back
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentDTO {
        private Long documentId;
        private String side; // FRONT, BACK
        private String imageUrl;
        private String status; // PENDING, APPROVED, REJECTED
        private String reviewNote;
        private LocalDateTime uploadedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatisticsDTO {
        private int groupsJoined; // Số nhóm đã tham gia
        private String accountStatus; // Active/Inactive
        private LocalDateTime memberSince; // Active member since
    }
}
