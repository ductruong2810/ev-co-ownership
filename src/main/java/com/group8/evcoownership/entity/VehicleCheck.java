package com.group8.evcoownership.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "VehicleCheck")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleCheck {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BookingId")
    @JsonIgnoreProperties({"vehicleChecks", "user", "vehicle", "handler", "hibernateLazyInitializer"})
    private UsageBooking booking;

    @Column(name = "CheckType", length = 20)
    private String checkType; // PRE_USE, POST_USE, REJECTION

    @Column(name = "Odometer")
    private Integer odometer;

    @Column(name = "BatteryLevel", precision = 5, scale = 2)
    private BigDecimal batteryLevel;

    @Column(name = "Cleanliness", length = 20)
    private String cleanliness; // CLEAN, DIRTY, VERY_DIRTY

    @Nationalized
    @Lob
    @Column(name = "Notes")
    private String notes;

    @Nationalized
    @Lob
    @Column(name = "Issues")
    private String issues; // JSON array of issues

    @Column(name = "Status", length = 20)
    private String status; // PASSED, REJECTED, PENDING

    @CreationTimestamp
    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;
}
