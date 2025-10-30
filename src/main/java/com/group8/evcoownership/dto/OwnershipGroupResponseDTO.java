package com.group8.evcoownership.dto;


import com.group8.evcoownership.enums.GroupStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OwnershipGroupResponseDTO(
        Long groupId,
        String groupName,
        String description,
        Integer memberCapacity,
        GroupStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String userRole,           // ADMIN hoặc MEMBER
        Boolean isGroupAdmin,      // true nếu user là admin của group
        Boolean isGroupMember,     // true nếu user là member của group
        BigDecimal ownershipPercentage // tỷ lệ sở hữu của user trong group
) {
    // Constructor cho backward compatibility
    public OwnershipGroupResponseDTO(Long groupId, String groupName, String description,
                                     Integer memberCapacity, GroupStatus status,
                                     LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(groupId, groupName, description, memberCapacity, status, createdAt, updatedAt,
                null, false, false, null);
    }
}