package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.*;

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
    @Column(name = "VehicleID")
    private Long vehicleId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GroupID", nullable = false, unique = true)
    private OwnershipGroup group;

    @Column(name = "Brand", length = 100)
    private String brand;

    @Column(name = "Model", length = 100)
    private String model;

    @Column(name = "LicensePlate", unique = true, length = 20)
    private String licensePlate;

    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    // Tự động gán khi insert
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    // Tự động gán khi update
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }}
