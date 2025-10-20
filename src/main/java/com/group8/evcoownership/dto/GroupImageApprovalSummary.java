package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.GroupStatus;
import lombok.Builder;

@Builder
public record GroupImageApprovalSummary(
        Long groupId,
        String groupName,
        int totalImages,
        int pendingImages,
        int approvedImages,
        int rejectedImages,
        GroupStatus groupStatus
) {
}
