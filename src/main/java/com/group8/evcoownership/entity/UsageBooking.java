package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.BookingStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

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

    @Column(name = "QrCode", length = 255)
    private String qrCode;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @Column(name = "QrCodeCheckin", length = 255)
    private String qrCodeCheckin;

    @Column(name = "QrCodeCheckout", length = 255)
    private String qrCodeCheckout;

    @Column(name = "CheckinStatus")
    private Boolean checkinStatus = false;

    @Column(name = "CheckoutStatus")
    private Boolean checkoutStatus = false;

    @Column(name = "CheckinTime")
    private LocalDateTime checkinTime;

    @Column(name = "CheckoutTime")
    private LocalDateTime checkoutTime;

    // Relationships với các entity mới
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VehicleCheck> vehicleChecks;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Incident> incidents;

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