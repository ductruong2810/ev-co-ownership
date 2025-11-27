package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.DisputeStatus;
import com.group8.evcoownership.enums.DisputeType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
    private Long id;

    // FK → OwnershipGroup (tranh chấp liên quan đến nhóm)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "GroupId", nullable = false)
    private OwnershipGroup group;

    // FK → Users (người tạo tranh chấp - CO_OWNER)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CreatedBy", nullable = false)
    private User createdBy;

    // Loại tranh chấp
    @Enumerated(EnumType.STRING)
    @Column(name = "DisputeType", length = 20, nullable = false)
    private DisputeType disputeType;

    // Trạng thái tranh chấp
    @Enumerated(EnumType.STRING)
    @Column(name = "Status", length = 20, nullable = false)
    @Builder.Default
    private DisputeStatus status = DisputeStatus.OPEN;

    // Tiêu đề tranh chấp
    @Nationalized
    @Column(name = "Title", length = 255, nullable = false)
    private String title;

    // Mô tả chi tiết tranh chấp
    @Nationalized
    @Lob
    @Column(name = "Description")
    private String description;

    // FK → Users (staff/admin giải quyết)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ResolvedBy")
    private User resolvedBy;

    // Ghi chú giải quyết
    @Nationalized
    @Lob
    @Column(name = "ResolutionNote")
    private String resolutionNote;

    // Thời điểm giải quyết
    @Column(name = "ResolvedAt")
    private LocalDateTime resolvedAt;

    // Thời điểm tạo
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Thời điểm cập nhật
    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;

    // Auto set timestamp & default status
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = DisputeStatus.OPEN;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

