package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.PaymentStatus;
import com.group8.evcoownership.enums.PaymentType;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdatePaymentRequest {
    private Long userId;
    @DecimalMin("0.01")
    private BigDecimal amount;
    private String paymentMethod;
    private PaymentType paymentType;
    private PaymentStatus status;
    private String transactionCode;
    private String providerResponse;
}