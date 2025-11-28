package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for individual uploaded document (FRONT or BACK)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadedDocumentDTO {
    private Long documentId;
    private String imageUrl;
    private String status;
    private String documentNumber;
}

