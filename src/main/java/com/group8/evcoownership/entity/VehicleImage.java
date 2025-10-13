package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "VehicleImages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ImageId")
    private Long imageId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VehicleId")
    private Vehicle vehicle;
    
    @Column(name = "ImageUrl", length = 500)
    private String imageUrl;
    
    @Column(name = "ImageType", length = 20)
    private String imageType; // 'VEHICLE' hoặc 'LICENSE'
    
    @Column(name = "UploadedAt")
    private LocalDateTime uploadedAt;
    
    @PrePersist
    public void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
}
