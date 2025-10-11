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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BookingId")
    private UsageBooking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ReportedBy")
    private User reportedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "ReportType", length = 20)
    private ReportType reportType;

    @Column(name = "Odometer")
    private Integer odometer;

    @Column(name = "BatteryLevel", precision = 5, scale = 2)
    private BigDecimal batteryLevel;

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

    @Nationalized
    @Lob
    @Column(name = "RejectionReason")
    private String rejectionReason;

    @Nationalized
    @Lob
    @Column(name = "ResolutionNotes")
    private String resolutionNotes;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
    }

}