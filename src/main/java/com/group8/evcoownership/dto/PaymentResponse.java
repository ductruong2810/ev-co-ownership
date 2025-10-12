package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {
    private Long id;                // PaymentId
    private Long userId;            // từ User.userId
    private Long fundId;
    private String userFullName;    // từ User.fullName
    private BigDecimal amount;
    private LocalDateTime paymentDate;
    private String paymentMethod;
    private String status;
    private String transactionCode;
    private String providerResponse;
    private String paymentType;
}