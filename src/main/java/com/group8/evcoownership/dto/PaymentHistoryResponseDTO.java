package com.group8.evcoownership.dto;

import java.math.BigDecimal;
import java.util.List;

public class PaymentHistoryResponseDTO {
    private Long userId;
    private Long groupId;

    private int page;
    private int size;
    private long total;

    private BigDecimal totalCompletedAmount;   // tổng tiền trạng thái COMPLETED theo bộ lọc ngày

    private List<PaymentHistoryItemDTO> items; // danh sách giao dịch
}
