package com.group8.evcoownership.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdatePaymentRequestDTO {
    private Long userId;
    @DecimalMin("0.01")
    private BigDecimal amount;
    private String paymentMethod;
    private String paymentType;
    private String status;
    private String transactionCode;
    private String providerResponse;
}