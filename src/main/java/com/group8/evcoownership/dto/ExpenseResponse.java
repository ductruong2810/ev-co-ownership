package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ExpenseResponse {
    private Long id;
    private Long fundId;
    private String sourceType;
    private Long sourceId;
    private String description;
    private BigDecimal amount;
    private LocalDateTime expenseDate;
}