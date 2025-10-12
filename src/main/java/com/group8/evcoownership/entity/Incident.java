package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Incident")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IncidentId", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "BookingId", nullable = false)
    private UsageBooking booking;

    @Column(name = "IncidentType", length = 50)
    private String incidentType; // BATTERY_FAILURE, ACCIDENT, TECHNICAL_ISSUE, DAMAGE

    @Nationalized
    @Lob
    @Column(name = "Description")
    private String description;

    @Column(name = "EstimatedCost", precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "ActualCost", precision = 12, scale = 2)
    private BigDecimal actualCost;

    @Column(name = "Status", length = 20)
    private String status; // REPORTED, INVESTIGATING, RESOLVED

    @Nationalized
    @Lob
    @Column(name = "ImageUrls")
    private String imageUrls; // JSON array of image URLs

    @Column(name = "IncidentDate")
    private LocalDateTime incidentDate;

    @Column(name = "ResolvedDate")
    private LocalDateTime resolvedDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ResolvedBy")
    private User resolvedBy;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
        if (incidentDate == null) {
            incidentDate = LocalDateTime.now();
        }
    }
}