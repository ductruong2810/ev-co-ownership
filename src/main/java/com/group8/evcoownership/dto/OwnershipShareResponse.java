package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.DepositStatus;
import com.group8.evcoownership.enums.GroupRole;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OwnershipShareResponse(
        Long userId,
        Long groupId,
        GroupRole groupRole,
        BigDecimal ownershipPercentage,
        DepositStatus depositStatus,
        LocalDateTime joinDate,
        LocalDateTime updatedAt
) {}