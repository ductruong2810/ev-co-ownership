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
    private String imageUrl;
    private String status;         // PENDING, APPROVED, REJECTED
    private LocalDateTime uploadedAt;
}
