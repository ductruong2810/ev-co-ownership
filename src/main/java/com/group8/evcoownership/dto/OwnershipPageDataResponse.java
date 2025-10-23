package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnershipPageDataResponse {
    private OwnershipPercentageResponse userOwnership;
    private GroupOwnershipSummaryResponse groupSummary;
    private List<BigDecimal> suggestions;
    private VehicleResponse vehicleInfo; // Sử dụng VehicleResponse có sẵn
}
