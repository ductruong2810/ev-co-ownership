package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.GroupStatus;
import lombok.Builder;

@Builder
public record GroupApprovalResult(
        Long groupId,
        int totalImages,
        int approvedImages,
        int rejectedImages,
        GroupStatus groupStatus
) {
}
