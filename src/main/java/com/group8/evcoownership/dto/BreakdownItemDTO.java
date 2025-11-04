package com.group8.evcoownership.dto;

import java.math.BigDecimal;

public record BreakdownItemDTO(
        String txnType,     // PAYMENT | EXPENSE
        String sourceType,  // CONTRIBUTION | INCIDENT | MAINTENANCE | ...
        BigDecimal income,  // >= 0
        BigDecimal expense  // >= 0
) {}