package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponseDTO {
    private Long id;
    private Long userId;
    private Long fundId;
    private String userFullName;
    private BigDecimal amount;
    private LocalDateTime paymentDate;
    private String paymentMethod;
    private String status;
    private String transactionCode;
    private String providerResponse;
    private String paymentType;

    //Thêm trường này để gửi link VNPAY ngay sau khi tạo payment
    private String paymentUrl;
}