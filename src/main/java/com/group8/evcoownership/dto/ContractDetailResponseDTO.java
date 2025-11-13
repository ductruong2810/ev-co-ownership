package com.group8.evcoownership.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.group8.evcoownership.enums.ContractApprovalStatus;
import com.group8.evcoownership.enums.DepositStatus;
import com.group8.evcoownership.enums.GroupStatus;
import com.group8.evcoownership.enums.GroupRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContractDetailResponseDTO {
    private ContractInfo contract;
    private GroupInfo group;
    private List<MemberInfo> members;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ContractInfo {
        private Long contractId;
        private String terms;
        private BigDecimal requiredDepositAmount;
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalDateTime depositDeadline;
        private Boolean isActive;
        private ContractApprovalStatus approvalStatus;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GroupInfo {
        private Long groupId;
        private String groupName;
        private GroupStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MemberInfo {
        private Long userId;
        private String fullName;
        private String email;
        private GroupRole userRole;
        private BigDecimal ownershipPercentage;
        private DepositStatus depositStatus;
        private LocalDateTime joinDate;
    }
}

