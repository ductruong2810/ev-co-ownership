package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.MaintenanceCoverageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

    private String liableUserName;              // co-owner phải trả
    private MaintenanceCoverageType coverageType; // PERSONAL OR GROUP

    private String description;

    // Giá sửa chính thức
    private BigDecimal actualCost;

    // PENDING | APPROVED | FUNDED | IN_PROGRESS | COMPLETED | REJECTED
    private String status;

    // Thời điểm technician gửi yêu cầu
    private LocalDateTime requestDate;

    // Thời điểm staff duyệt
    private LocalDateTime approvalDate;

    private LocalDate nextDueDate;       // ngày bảo trì định kỳ kế tiếp


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

    // list chua thong tin Payer va ownershíphare
    private List<MaintenancePayerShareDTO> payerShares;

}
