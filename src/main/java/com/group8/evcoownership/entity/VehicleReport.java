package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.Cleanliness;
import com.group8.evcoownership.enums.ReportType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "VehicleReport")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ReportId", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "ReportType", length = 20)
    private ReportType reportType;

    @Column(name = "Mileage")
    private Integer mileage;

    @Column(name = "ChargeLevel", precision = 5, scale = 2)
    private BigDecimal chargeLevel;

    @Nationalized
    @Lob
    @Column(name = "Damages")
    private String damages;

    @Enumerated(EnumType.STRING)
    @Column(name = "Cleanliness", length = 20)
    private Cleanliness cleanliness;

    @Nationalized
    @Lob
    @Column(name = "Notes")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TechnicianId")
    private User technician;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
    }

}