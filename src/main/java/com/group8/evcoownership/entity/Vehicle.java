package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "Vehicle")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VehicleId", nullable = false)
    private Long id;

    @Size(max = 100)
    @Nationalized
    @Column(name = "Brand", length = 100)
    private String brand;

    @Size(max = 100)
    @Nationalized
    @Column(name = "Model", length = 100)
    private String model;

    @Size(max = 20)
    @Nationalized
    @Column(name = "LicensePlate", length = 20)
    private String licensePlate;

    @Size(max = 30)
    @Nationalized
    @Column(name = "ChassisNumber", length = 30)
    private String chassisNumber; // VIN / số khung xe

    @Column(name = "QrCode")
    private String qrCode;

    @Column(name = "VehicleValue", precision = 15, scale = 2)
    private BigDecimal vehicleValue;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GroupId")
    private OwnershipGroup ownershipGroup;

    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VehicleImage> images;

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