package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.RejectionReason;
import com.group8.evcoownership.enums.RejectionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Entity
@Table(name = "VehicleRejection")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleRejection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RejectionId", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VehicleId")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BookingId")
    private UsageBooking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RejectedBy")
    private User rejectedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "RejectionReason", length = 20)
    private RejectionReason rejectionReason;

    @Nationalized
    @Lob
    @Column(name = "DetailedReason")
    private String detailedReason;

    @Nationalized
    @Lob
    @Column(name = "Photos")
    private String photos; // Comma-separated photo URLs

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", length = 20)
    private RejectionStatus status;

    @Column(name = "RejectedAt")
    private LocalDateTime rejectedAt;

    @Column(name = "ResolvedAt")
    private LocalDateTime resolvedAt;

    @PrePersist
    public void onCreate() {
        rejectedAt = LocalDateTime.now();
    }
}
