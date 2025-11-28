package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.MaintenanceCoverageType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "Maintenance")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Maintenance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaintenanceId", nullable = false)
    private Long id;

    // FK → Vehicle (NOT NULL)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "VehicleId", nullable = false)
    private Vehicle vehicle;

    // FK → Users (technician phát hiện)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "RequestedBy", nullable = false)
    private User requestedBy;

    // FK → Users (staff duyệt, có thể null)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ApprovedBy")
    private User approvedBy;

    // FK → Users (co-owner làm hư xe / người phải trả tiền)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LiableUserId")
    private User liableUser;

    @Column(name = "Description", columnDefinition = "TEXT")
    private String description;

    // Giá sửa chính thức (technician nhập, không đổi nữa)
    @NotNull
    @Column(name = "ActualCost", precision = 12, scale = 2, nullable = false)
    private BigDecimal actualCost;

    @Column(name = "Status", length = 20, nullable = false)
    private String status; // PENDING | APPROVED | FUNDED | IN_PROGRESS | COMPLETED | REJECTED

    // Loại coverage: GROUP_FUND (dùng quỹ) | PERSONAL (co-owner tự trả)
    @Enumerated(EnumType.STRING)
    @Column(name = "CoverageType", length = 20, nullable = false)
    private MaintenanceCoverageType coverageType;

    // Ngày technician tạo request
    @Column(name = "RequestDate", nullable = false)
    private LocalDateTime requestDate;

    // Ngày staff duyệt (APPROVED)
    @Column(name = "ApprovalDate")
    private LocalDateTime approvalDate;

    @Column(name = "NextDueDate")
    private LocalDate nextDueDate;

    // Số ngày dự kiến xe nằm gara (vd: 3 ngày)
    @Column(name = "EstimatedDurationDays")
    private Integer estimatedDurationDays;

    // Thời điểm bắt đầu bảo trì (staff chuyển sang IN_PROGRESS)
    @Column(name = "MaintenanceStartAt")
    private LocalDateTime maintenanceStartAt;

    // Thời điểm dự kiến hoàn tất (maintenanceStartAt + estimatedDurationDays)
    @Column(name = "ExpectedFinishAt")
    private LocalDateTime expectedFinishAt;

    // Thời điểm thực tế hoàn tất (staff bấm COMPLETED)
    @Column(name = "MaintenanceCompletedAt")
    private LocalDateTime maintenanceCompletedAt;

    // Thời điểm maintenance được fund đủ (GROUP_FUND: quỹ đủ / PERSONAL: user trả xong)
    @Column(name = "FundedAt")
    private LocalDateTime fundedAt;

    // Ngày tạo bản ghi
    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    // Ngày cập nhật cuối
    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;

    // =======================
    // Lifecycle hooks
    // =======================

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.requestDate == null) {
            this.requestDate = now;
        }
        if (this.status == null) {
            this.status = "PENDING";
        }
        if (this.coverageType == null) {
            this.coverageType = MaintenanceCoverageType.GROUP;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
