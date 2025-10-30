package com.group8.evcoownership.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SharedFundDTO(
        Long fundId,
        Long groupId,
        BigDecimal balance,
        BigDecimal targetAmount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

