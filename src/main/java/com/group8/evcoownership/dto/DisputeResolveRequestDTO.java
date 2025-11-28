package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.DisputeStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeResolveRequestDTO {

    @NotNull(message = "Status is required")
    private DisputeStatus status; // RESOLVED or REJECTED

    private String resolutionNote; // optional, ghi chú giải quyết
}


