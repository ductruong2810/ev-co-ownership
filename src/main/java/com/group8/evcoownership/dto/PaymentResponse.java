package com.group8.evcoownership.dto;


import com.group8.evcoownership.enums.PaymentStatus;
import com.group8.evcoownership.enums.PaymentType;
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
    private PaymentStatus status;
    private String transactionCode;
    private String providerResponse;
    private PaymentType paymentType;
}