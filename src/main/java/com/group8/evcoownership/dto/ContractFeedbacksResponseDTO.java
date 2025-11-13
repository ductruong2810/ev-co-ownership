package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.ContractApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractFeedbacksResponseDTO {
    private Long contractId;
    private ContractApprovalStatus contractStatus;
    private Integer totalMembers;
    private Integer totalFeedbacks;
    private Long acceptedCount;
    private Long pendingDisagreeCount;
    private Long approvedFeedbacksCount;  // Tổng số feedback đã được admin approve
    private Long rejectedFeedbacksCount;  // Tổng số feedback đã được admin reject
    private List<ContractFeedbackResponseDTO> feedbacks;
    private List<PendingMemberDTO> pendingMembers;
}

