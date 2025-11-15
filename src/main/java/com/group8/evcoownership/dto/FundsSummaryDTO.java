package com.group8.evcoownership.dto;

import java.math.BigDecimal;

public record FundsSummaryDTO(
        Long groupId,
        BigDecimal operatingBalance,  // tiền chi được
        BigDecimal depositBalance,    // tiền cọc (khóa)
        BigDecimal totalBalance       // tổng = operating + deposit
) {
}
