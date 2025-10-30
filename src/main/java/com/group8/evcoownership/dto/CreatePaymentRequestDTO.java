package com.group8.evcoownership.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePaymentRequestDTO {
    @NotNull
    private Long userId;
    private Long fundId;
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    private String paymentMethod;
    private String paymentType;
    private String transactionCode;
}