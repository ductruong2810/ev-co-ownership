package com.group8.evcoownership.dto;

import java.math.BigDecimal;
import java.util.List;

// Tổng hợp + nhúng luôn các dòng sổ quỹ
public record LedgerSummaryDTO(
        BigDecimal totalIn,          // tổng thu (Payment COMPLETED)
        BigDecimal totalOut,         // tổng chi (Expense APPROVED/PAID)
        BigDecimal operatingBalance, // số dư quỹ OPERATING hiện tại
        BigDecimal depositBalance,   // số dư quỹ DEPOSIT_RESERVE hiện tại
        List<LedgerRowDTO> rows      // các dòng in/out đã map trước đó
) {}