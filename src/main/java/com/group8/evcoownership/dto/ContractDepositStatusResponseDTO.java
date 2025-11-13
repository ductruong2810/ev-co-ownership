package com.group8.evcoownership.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.group8.evcoownership.enums.ContractApprovalStatus;
import com.group8.evcoownership.enums.DepositStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContractDepositStatusResponseDTO {
    private Long groupId;
    private Long contractId;
    private ContractApprovalStatus approvalStatus;
    private BigDecimal requiredDepositAmount;
    private BigDecimal totalPaid;
    private BigDecimal remaining;
    private Boolean isFullyPaid;
    private Integer totalMembers;
    private Integer paidMembers;
    private Boolean allMembersPaid;
    private String paymentProgress;
    private List<MemberDepositStatus> memberDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MemberDepositStatus {
        private Long userId;
        private String fullName;
        private BigDecimal ownershipPercentage;
        private BigDecimal requiredDeposit;
        private DepositStatus depositStatus;
        private Boolean isPaid;
    }
}

