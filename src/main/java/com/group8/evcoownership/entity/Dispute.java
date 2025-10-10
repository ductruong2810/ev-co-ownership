package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Entity
@Table(name = "Dispute")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DisputeId", nullable = false)
    private Long disputeId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "FundId", nullable = false)
    private SharedFund fund;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserId", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VehicleReportId")
    private VehicleReport vehicleReport;

    @Nationalized
    @Lob
    @Column(name = "Description")
    private String description;

    @Size(max = 20)
    @ColumnDefault("'Open'")
    @Column(name = "Status", length = 20)
    private String status;

    @Nationalized
    @Lob
    @Column(name = "ResolutionNote")
    private String resolutionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ResolvedBy")
    private User resolvedBy;

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