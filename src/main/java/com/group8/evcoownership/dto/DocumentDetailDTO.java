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
public class DocumentDetailDTO {
    private Long documentId;

    @Builder.Default
    private String imageUrl = "";

    @Builder.Default
    private String status = "";         // PENDING, APPROVED, REJECTED
    private LocalDateTime uploadedAt;

    @Builder.Default
    private String reviewNote = "";

    @Builder.Default
    private String reviewedBy = "";

    @Builder.Default
    private String documentNumber = "";

    @Builder.Default
    private String dateOfBirth = "";

    @Builder.Default
    private String issueDate = "";

    @Builder.Default
    private String expiryDate = "";

    @Builder.Default
    private String address = "";
}
