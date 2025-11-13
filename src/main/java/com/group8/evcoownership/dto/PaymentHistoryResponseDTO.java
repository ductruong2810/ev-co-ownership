package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHistoryResponseDTO {
    private Long userId;
    private BigDecimal totalCompletedAmount;   // tổng tiền trạng thái COMPLETED theo bộ lọc ngày

    private List<PaymentHistoryItemDTO> items; // danh sách giao dịch
}
