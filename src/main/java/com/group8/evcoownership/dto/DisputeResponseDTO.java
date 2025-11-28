package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.DisputeStatus;
import com.group8.evcoownership.enums.DisputeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeResponseDTO {
    private Long id;
    private Long groupId;
    private String groupName;
    private Long createdById;
    private String createdByName;
    private String createdByEmail;
    private DisputeType disputeType;
    private DisputeStatus status;
    private String title;
    private String description;
    private Long resolvedById;
    private String resolvedByName;
    private String resolutionNote;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


