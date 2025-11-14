package com.group8.evcoownership.dto;

import lombok.*;
import java.math.BigDecimal;
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
    private String approvedByName;  // Staff/Admin

    private String description;

    // Giá sửa chính thức
    private BigDecimal actualCost;

    // PENDING | APPROVED | FUNDED | IN_PROGRESS | COMPLETED | REJECTED
    private String status;

    // Thời điểm technician gửi yêu cầu
    private LocalDateTime requestDate;

    // Thời điểm staff duyệt
    private LocalDateTime approvalDate;

    // ====== Các thông tin về thời gian bảo trì ======

    // Số ngày dự kiến xe nằm gara (vd: 3 ngày)
    private Integer estimatedDurationDays;

    // Lúc bắt đầu bảo trì (staff set IN_PROGRESS)
    private LocalDateTime maintenanceStartAt;

    // Dự kiến hoàn tất (start + estimatedDurationDays)
    private LocalDateTime expectedFinishAt;

    // Thời điểm thực tế hoàn tất (staff bấm COMPLETED)
    private LocalDateTime maintenanceCompletedAt;

    // Ngày tạo bản ghi
    private LocalDateTime createdAt;

    // Ngày cập nhật gần nhất
    private LocalDateTime updatedAt;
}
