package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class GroupOwnershipSummaryResponseDTO {
    private Long groupId;
    private String groupName;
    private BigDecimal vehicleValue;
    private int totalMembers;
    private Integer memberCapacity;
    private BigDecimal totalAllocatedPercentage;
    private boolean isFullyAllocated;
    private BigDecimal remainingPercentage;
    private String currentUserRole;
    private List<MemberOwnershipInfo> members;

    @Data
    @Builder
    public static class MemberOwnershipInfo {
        private Long userId;
        private String userName;
        private String userEmail;
        private BigDecimal ownershipPercentage;
        private BigDecimal investmentAmount;
        private String status; // PENDING, CONFIRMED, LOCKED
        private boolean isCurrentUser;
        private String groupRole;// Them vai tro cua user
    }
}
