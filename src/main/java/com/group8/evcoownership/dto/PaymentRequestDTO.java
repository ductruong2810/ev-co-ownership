package com.group8.evcoownership.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequestDTO {
    private long userId;
    private Long groupId;
    private long fundId;
    private BigDecimal amount;
    private String paymentMethod;   // "VNPAY" | "BANK" | "CASH"
    private String paymentType;     // "DEPOSIT" | "FUND"
    private String transactionCode;
    private String paymentCategory;// optional
}
