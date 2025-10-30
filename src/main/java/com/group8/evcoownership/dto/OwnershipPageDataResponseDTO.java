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
public class OwnershipPageDataResponseDTO {
    private OwnershipPercentageResponseDTO userOwnership;
    private GroupOwnershipSummaryResponseDTO groupSummary;
    private List<BigDecimal> suggestions;
    private VehicleResponseDTO vehicleInfo; // Sử dụng VehicleResponse có sẵn
}
