package com.group8.evcoownership.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentHistoryItemDTO {
    private Long paymentId;
    private Long fundId;
    private BigDecimal amount;
    private String paymentMethod;   // VNPay / BANK_TRANSFER / CASH...
    private String status;          // enum name: PENDING / COMPLETED / FAILED / REFUNDED
    private String paymentType;     // enum name: DEPOSIT / CONTRIBUTION / ...
    private String transactionCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime paymentDate;
}
