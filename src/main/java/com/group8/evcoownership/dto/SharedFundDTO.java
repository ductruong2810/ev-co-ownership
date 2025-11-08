package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.FundType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SharedFundDTO(
        Long fundId,
        Long groupId,
        FundType fundType,      // NEW
        boolean spendable,      // NEW
        BigDecimal balance,
        BigDecimal targetAmount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

