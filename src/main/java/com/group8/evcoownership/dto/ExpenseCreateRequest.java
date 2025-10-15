package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ExpenseCreateRequest {
    @NotNull private Long fundId;
    @NotNull private BigDecimal amount;
    private String sourceType;
    private Long sourceId;
    private String description;
    private LocalDateTime expenseDate;
}
