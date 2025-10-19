package com.group8.evcoownership.dto;

import java.time.LocalDateTime;

public record ContractGenerationResponse(
        Long contractId,
        String contractNumber,
        String htmlContent,
        String pdfUrl,
        LocalDateTime generatedAt,
        String status
) {
}
