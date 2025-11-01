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

    private String requestedByName; // technician
    private String approvedByName; // Staff/Admin

    private String description;

    private BigDecimal actualCost;

    private String status;

    private LocalDateTime requestDate;   // thời điểm technician gửi yêu cầu
    private LocalDateTime approvalDate;  // thời điểm staff duyệt
    private LocalDate nextDueDate;       // ngày bảo trì định kỳ kế tiếp

    private LocalDateTime createdAt;     // ngày tạo bản ghi
    private LocalDateTime updatedAt;     // ngày cập nhật gần nhất
}
