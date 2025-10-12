package com.group8.evcoownership.dto;


import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DisputeCreateRequest {
    @NotNull private Long fundId;
    @NotNull private Long userId;
    private Long vehicleReportId;   // optional
    private String description;     // optional
}
