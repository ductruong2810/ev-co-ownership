package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DisputeStatusUpdateRequest {
    @NotBlank
    private String status;          // Open | Resolved | Rejected
    private String resolutionNote;  // optional
    private Long resolvedByUserId;  // optional
}