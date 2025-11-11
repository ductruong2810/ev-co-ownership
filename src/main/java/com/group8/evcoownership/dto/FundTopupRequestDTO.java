package com.group8.evcoownership.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class FundTopupRequestDTO {
    @NotNull
    Long groupId;
    @NotNull Long fundId;          // Operating fund
    @NotNull @Min(1000)
    BigDecimal amount;
    String note;
}
