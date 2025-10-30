package com.group8.evcoownership.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceResponseDTO {
    private Long id;
    private Long vehicleId;
    private String vehicleModel;
    private String requestedByName;
    private String approvedByName;
    private String description;
    private BigDecimal actualCost;
    private String status;
    private LocalDate maintenanceDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

