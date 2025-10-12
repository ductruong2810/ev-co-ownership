package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "VehicleId", nullable = false)
    private Vehicle vehicle;

    @Column(name = "RequestedBy", nullable = false)
    private Long requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ApprovedBy")
    private User approvedBy;

    @Column(name = "RequestDate")
    private LocalDateTime requestDate;

    @Column(name = "ApprovalDate")
    private LocalDateTime approvalDate;

    @Column(name = "NextDueDate")
    private LocalDate nextDueDate;

    @Nationalized
    @Lob
    @Column(name = "Description")
    private String description;

    @Column(name = "EstimatedCost", precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "ActualCost", precision = 12, scale = 2)
    private BigDecimal actualCost;

    @Column(name = "MaintenanceStatus", length = 20)
    private String maintenanceStatus; // PENDING, APPROVED, IN_PROGRESS, COMPLETED, CANCELLED

}