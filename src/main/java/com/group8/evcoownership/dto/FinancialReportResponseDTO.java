package com.group8.evcoownership.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FinancialReportResponseDTO(
        Long reportId,
        Long fundId,
        Integer reportYear,
        Integer reportMonth,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal openingBalance,   // nullable (chỉ có khi generate)
        BigDecimal closingBalance,   // nullable (chỉ có khi generate)
        Long generatedBy,
        String generatedByName,      // nullable
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}