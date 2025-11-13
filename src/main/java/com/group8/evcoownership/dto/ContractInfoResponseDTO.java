package com.group8.evcoownership.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContractInfoResponseDTO {
    private Long contractId;
    private Long groupId;
    private String groupName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String terms;
    private BigDecimal requiredDepositAmount;
    private LocalDateTime depositDeadline;
    private Boolean isActive;
    private ContractApprovalStatus approvalStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean savedToDatabase;
    private String templateId;
}

