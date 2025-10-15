package com.group8.evcoownership.dto;

import com.group8.evcoownership.entity.UserDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDocumentDTO {

    private Long documentId;
    private Long userId;
    private String documentType;  // DRIVER_LICENSE, CITIZEN_ID
    private String side;           // FRONT, BACK
    private String imageUrl;
    private String status;         // PENDING, APPROVED, REJECTED
    private String reviewNote;
    private Long reviewedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Helper method để convert từ Entity
    public static UserDocumentDTO fromEntity(UserDocument entity) {
        if (entity == null) {
            return null;
        }

        return UserDocumentDTO.builder()
                .documentId(entity.getDocumentId())
                .userId(entity.getUserId())
                .documentType(entity.getDocumentType())
                .side(entity.getSide())
                .imageUrl(entity.getImageUrl())
                .status(entity.getStatus())
                .reviewNote(entity.getReviewNote())
                .reviewedBy(entity.getReviewedBy() != null ?
                        entity.getReviewedBy().getUserId() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
