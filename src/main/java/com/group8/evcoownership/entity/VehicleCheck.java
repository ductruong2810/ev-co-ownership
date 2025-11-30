package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

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

    // 1) Quan hệ tới Booking nhưng KHÔNG trả ra JSON
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BookingId")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private UsageBooking booking;

    // 2) Mapping trực tiếp cột BookingId để đọc FK
    @Column(name = "BookingId", insertable = false, updatable = false)
    private Long bookingId;

    @Column(name = "CheckType", length = 20)
    private String checkType; // PRE_USE, POST_USE, REJECTION

    @Column(name = "Odometer")
    private Integer odometer;

    @Column(name = "BatteryLevel", precision = 5, scale = 2)
    private BigDecimal batteryLevel;

    @Column(name = "Cleanliness", length = 20)
    private String cleanliness; // CLEAN, DIRTY, VERY_DIRTY

    @Column(name = "Notes")
    private String notes;

    @Column(name = "Issues")
    private String issues; // JSON array of issues

    @Column(name = "Status", length = 20)
    private String status; // PASSED, REJECTED, PENDING

    @CreationTimestamp
    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;
}
