package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.FundType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// direction: "IN" (nạp) | "OUT" (chi)
// title: IN = tên người nạp; OUT = nhãn theo sourceType (vd: "Bảo Trì & Chi Phí")
// subtitle: IN = vai trò hiển thị; OUT = Expense.description
public record LedgerRowDTO(
        String direction,
        Long fundId,
        FundType fundType,
        String title,
        String subtitle,
        Long userId,                 // IN: payerId; OUT: recipientUserId (có thể null)
        BigDecimal amount,
        LocalDateTime occurredAt
) {}