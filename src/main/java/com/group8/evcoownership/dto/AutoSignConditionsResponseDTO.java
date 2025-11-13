package com.group8.evcoownership.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AutoSignConditionsResponseDTO {
    private Boolean hasEnoughMembers;
    private Integer currentMembers;
    private Integer requiredMembers;
    private Boolean hasCorrectOwnershipPercentage;
    private BigDecimal totalOwnershipPercentage;
    private BigDecimal expectedOwnershipPercentage;
    private Boolean hasValidOwnershipPercentages;
    private Integer membersWithZeroOrNullPercentage;
    private Boolean hasVehicle;
    private BigDecimal vehicleValue;
    private Boolean canSign;
    private String contractStatus;
    private Boolean allConditionsMet;
    private Boolean canAutoSign;
}

