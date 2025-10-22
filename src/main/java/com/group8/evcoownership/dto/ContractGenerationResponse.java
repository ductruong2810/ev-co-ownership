package com.group8.evcoownership.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record ContractGenerationResponse(
        Long contractId,
        String contractNumber,
        Map<String, Object> props,
        LocalDateTime generatedAt,
        String status
) {}
