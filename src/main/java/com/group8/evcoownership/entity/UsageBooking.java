package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.BookingStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "UsageBooking")
public class UsageBooking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BookingId", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserId", nullable = false)
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "VehicleId", nullable = false)
    private Vehicle vehicle;

    @Column(name = "StartDateTime")
    private LocalDateTime startDateTime;

    @Column(name = "EndDateTime")
    private LocalDateTime endDateTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", length = 20)
    private BookingStatus status;

    @Column(name = "TotalDuration")
    private Integer totalDuration;

    @Column(name = "Priority")
    private Integer priority;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}