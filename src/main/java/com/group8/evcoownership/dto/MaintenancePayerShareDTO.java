package com.group8.evcoownership.dto;


import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenancePayerShareDTO {

    private Long userId;
    private String fullName;

    // Tỷ lệ sở hữu (0.4, 0.35, ...)
    private BigDecimal ownershipRatio;

    // Số tiền phải đóng = totalCost * ownershipRatio
    private BigDecimal amount;
}