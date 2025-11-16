package com.group8.evcoownership.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseCreateRequestDTO {
    @NotNull(message = "Fund ID must not be null")
    private Long fundId;

    @NotBlank(message = "Source type must not be blank")
    private String sourceType;

    private Long sourceId; // optional — không bắt buộc

    @NotBlank(message = "Description must not be blank")
    private String description;

    @NotNull(message = "Amount must not be null")
    @DecimalMin(value = "0.01", inclusive = true, message = "Amount must be greater than 0")
    private BigDecimal amount;
}

