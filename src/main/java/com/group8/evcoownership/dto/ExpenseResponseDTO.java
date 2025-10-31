package com.group8.evcoownership.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseResponseDTO {
    private Long id;
    private String sourceType;
    private Long sourceId;
    private String description;
    private BigDecimal amount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expenseDate;
    private BigDecimal fundBalanceAfter;
    private Long approvedById;
    private Long recipientUserId;
}
