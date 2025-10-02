package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "CheckInOut")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckInOut {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CheckID")
    private Long checkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BookingID", nullable = false)
    private Booking booking;

    @Column(name = "CheckInTime")
    private LocalDateTime checkInTime;

    @Column(name = "CheckOutTime")
    private LocalDateTime checkOutTime;

    @Column(name = "Mileage")
    private Integer mileage;

    @Column(name = "Notes")
    private String notes;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    // Tự động gán khi update
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
