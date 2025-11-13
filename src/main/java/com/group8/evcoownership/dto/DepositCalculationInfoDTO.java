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
public class DepositCalculationInfoDTO {
    private BigDecimal calculatedDepositAmount;
    private String formattedAmount;
    private String explanation;
    private String note;
    private String calculationMethod;
    private BigDecimal vehicleValue;
    private String percentage;
    private Integer memberCapacity;
}

