package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response DTO for document upload endpoint (matches FE UploadImage type)
 * Used for /api/user/documents/upload-batch
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadResponseDTO {
    private Boolean success;
    private Map<String, UploadedDocumentDTO> uploadedDocuments; // FRONT, BACK
    private UserDocumentInfoDTO documentInfo;
    private String detectedType;
    private Boolean ocrEnabled;
    private String processingTime;
}
