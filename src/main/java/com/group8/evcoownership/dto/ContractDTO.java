package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.ContractApprovalStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ContractDTO {
    private Long id;
    private Long groupId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal requiredDepositAmount;
    private Boolean isActive;
    private ContractApprovalStatus approvalStatus;
    private Long approvedById;
    private LocalDateTime approvedAt;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
