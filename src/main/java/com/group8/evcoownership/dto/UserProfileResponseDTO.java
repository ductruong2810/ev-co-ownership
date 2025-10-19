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

    // ================= NESTED CLASSES =================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentsDTO {
        private DocumentTypeDTO citizenIdImages;      // CCCD
        private DocumentTypeDTO driverLicenseImages;  // GPLX
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentTypeDTO {
        // Front/Back là OBJECT (không phải string)
        private DocumentDetailDTO front;  // Object hoặc null
        private DocumentDetailDTO back;   // Object hoặc null
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentDetailDTO {
        private Long documentId;
        private String imageUrl;
        private String status;         // PENDING, APPROVED, REJECTED
        private String reviewNote;
        private LocalDateTime uploadedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatisticsDTO {
        private int groupsJoined;
        private String accountStatus;
        private LocalDateTime memberSince;
    }
}
