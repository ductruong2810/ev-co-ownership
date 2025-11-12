package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.ContractApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResubmitMemberApprovalResponseDTO {
    private Long contractId;
    private Long groupId;
    private ContractApprovalStatus approvalStatus;
    private Boolean feedbacksInvalidated;
}

