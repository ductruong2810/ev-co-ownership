package com.group8.evcoownership.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record LedgerEntryResponseDTO(
        OffsetDateTime txnDate,
        String txnType,     // PAYMENT | EXPENSE
        Long sourceId,      // PaymentId | ExpenseId
        String sourceType,
        BigDecimal amountSigned, // + thu, - chi
        String status,
        Long actorUserId
) {}
