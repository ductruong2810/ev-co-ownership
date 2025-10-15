package com.group8.evcoownership.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ExpenseUpdateRequest {
    private BigDecimal amount;
    private String sourceType;
    private Long sourceId;
    private String description;
    private LocalDateTime expenseDate;

}
