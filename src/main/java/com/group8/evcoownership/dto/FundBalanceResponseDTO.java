package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FundBalanceResponseDTO {
    private Long fundId;
    private Long groupId;
    private BigDecimal balance;
    private BigDecimal targetAmount;
}
