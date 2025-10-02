package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.ReportType;
import com.group8.evcoownership.enums.Cleanliness;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "VehicleReport")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ReportID")
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CheckID", nullable = false)
    private CheckInOut checkInOut;

    @Enumerated(EnumType.STRING)
    @Column(name = "ReportType")
    private ReportType reportType;

    @Column(name = "Mileage")
    private Integer mileage;

    @Column(name = "FuelOrChargeLevel", precision = 5, scale = 2)
    private Double fuelOrChargeLevel;

    @Column(name = "Damages", columnDefinition = "NVARCHAR(MAX)")
    private String damages;

    @Enumerated(EnumType.STRING)
    @Column(name = "Cleanliness")
    private Cleanliness cleanliness;

    @Column(name = "Notes", columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TechnicianID")
    private User technician;

    @Column(name = "CreatedAt", updatable = false, insertable = false)
    private LocalDateTime createdAt;
}
