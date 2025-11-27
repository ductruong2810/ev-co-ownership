package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for OCR preview endpoint (matches FE UploadImage type)
 * Used for /api/user/documents/preview-ocr
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentPreviewResponseDTO {
    private Boolean success;
    private String processingTime;
    private Integer textLength;
    private String extractedText;
    private Boolean isRegistrationDocument;
    private Boolean ocrEnabled;
    private String detectedType;
    private UserDocumentInfoDTO documentInfo;
}
