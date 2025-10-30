package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.Nationalized;

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

    @Nationalized
    @Lob
    @Column(name = "Description")
    private String description;

    @Column(name = "ActualCost", precision = 12, scale = 2)
    private BigDecimal actualCost;

    @Column(name = "Status", length = 20, nullable = false)
    private String status; // PENDING | APPROVED | REJECTED

    // Ngày dự kiến bảo dưỡng (do technician chọn)
    @Column(name = "MaintenanceDate", nullable = true)
    private LocalDate maintenanceDate;

    // Ngày tạo yêu cầu bảo trì
    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    // Ngày cập nhật (staff duyệt hoặc thay đổi trạng thái)
    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;

    // =======================
    // Lifecycle hooks
    // =======================

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null)
            this.status = "PENDING";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
