package com.group8.evcoownership.dto;


import com.group8.evcoownership.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;


public record PaymentStatusUpdateRequest(

        @NotNull PaymentStatus status,
        String transactionCode,       // bắt buộc khi chuyển sang Completed
        String providerResponseJson   // raw JSON từ gateway (nếu có)
) {
}