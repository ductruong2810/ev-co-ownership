package com.group8.evcoownership.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHistoryItemDTO {
    private Long paymentId;
    private Long fundId;
    private Long groupId;        // NEW
    private String groupName;   // NEW
    private BigDecimal amount;
    private String paymentMethod;   // VNPay / BANK_TRANSFER / CASH...
    private String status;          // enum name: PENDING / COMPLETED / FAILED / REFUNDED
    private String paymentType;     // enum name: DEPOSIT / CONTRIBUTION / ...
    private String transactionCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime paymentDate;


}
