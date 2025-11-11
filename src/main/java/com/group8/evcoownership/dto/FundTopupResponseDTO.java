package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.PaymentStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Builder
public record FundTopupResponseDTO(
        Long paymentId,
        Long userId,
        Long groupId,
        BigDecimal amount,
        BigDecimal requiredAmount,
        String paymentMethod,
        PaymentStatus status,
        String transactionCode,
        LocalDateTime paidAt,
        String vnpayUrl,
        String message
) {}
