package com.group8.evcoownership.dto;

import java.math.BigDecimal;
import java.util.List;

public record MonthlyBreakdownResponseDTO(
        Long fundId,
        Integer year,
        Integer month,
        List<BreakdownItemDTO> items,
        BigDecimal totalIncome,
        BigDecimal totalExpense
) {}