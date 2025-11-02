package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Vehicle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VehicleId", nullable = false)
    private Long Id;

    @Nationalized
    @Column(name = "Brand", length = 100)
    private String brand;

    @Nationalized
    @Column(name = "Model", length = 100)
    private String model;

    @Column(name = "LicensePlate", length = 20)
    private String licensePlate;

    @Column(name = "ChassisNumber", length = 30)
    private String chassisNumber;

    @Column(name = "VehicleValue", precision = 15, scale = 2)
    private BigDecimal vehicleValue;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GroupId")
    private OwnershipGroup ownershipGroup;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        final var now = LocalDateTime.now(); // nếu muốn UTC tuyệt đối: LocalDateTime.now(Clock.systemUTC())
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}