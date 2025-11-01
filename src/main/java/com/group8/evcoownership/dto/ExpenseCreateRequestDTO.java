package com.group8.evcoownership.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseCreateRequestDTO {
    private Long fundId;
    private String sourceType;
    private Long sourceId;
    private String description;
    private BigDecimal amount;
    private Long recipientUserId;
}

