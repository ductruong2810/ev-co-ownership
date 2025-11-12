package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.ContractApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractUpdateResponseDTO {
    private Long contractId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal requiredDepositAmount;
    private BigDecimal calculatedDepositAmount;
    private String depositCalculationExplanation;
    private String term;
    private String termExplanation;
    private ContractApprovalStatus approvalStatus;
    private LocalDateTime updatedAt;
}

