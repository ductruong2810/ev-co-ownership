package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class DisputeResponse {
    private Long disputeId;
    private Long fundId;
    private Long userId;
    private Long vehicleReportId;
    private String description;
    private String status;
    private String resolutionNote;
    private Long resolvedByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}