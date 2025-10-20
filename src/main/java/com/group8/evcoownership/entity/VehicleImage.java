package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.ImageApprovalStatus;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "ApprovalStatus", length = 20)
    private ImageApprovalStatus approvalStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ApprovedBy")
    private User approvedBy;

    @Column(name = "ApprovedAt")
    private LocalDateTime approvedAt;

    @Column(name = "RejectionReason", length = 500)
    private String rejectionReason;

    @Column(name = "UploadedAt")
    private LocalDateTime uploadedAt;

    @PrePersist
    public void onCreate() {
        uploadedAt = LocalDateTime.now();
        if (this.approvalStatus == null) {
            this.approvalStatus = ImageApprovalStatus.PENDING;
        }
    }
}
