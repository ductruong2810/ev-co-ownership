package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.DisputeStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
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

    // FK -> SharedFund(FundId)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "FundId", nullable = false)
    private SharedFund fund;

    // FK -> Users(UserId)  (người tạo dispute)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserId", nullable = false) // <-- THÊM ĐÚNG CỘT UserId
    private User createdBy;

    // Nếu vẫn muốn truy cập nhanh id (không bắt buộc):
    @Transient
    public Long getCreatedById() {
        return createdBy != null ? createdBy.getUserId() : null;
    }

    // ====== Các trường mở rộng của bạn ======

    @Column(name = "DisputeType", length = 50)
    private String disputeType; // FINANCIAL, USAGE, DECISION (cân nhắc enum riêng)

    @Column(name = "RelatedEntityType", length = 50)
    private String relatedEntityType; // INCIDENT, PAYMENT, EXPENSE, VOTING

    @Column(name = "RelatedEntityId")
    private Long relatedEntityId;

    @Nationalized
    @Lob
    @Column(name = "Description")
    private String description;

    @Column(name = "DisputedAmount", precision = 12, scale = 2)
    private BigDecimal disputedAmount;

    @Nationalized
    @Lob
    @Column(name = "Resolution")
    private String resolution;

    @Column(name = "ResolutionAmount", precision = 12, scale = 2)
    private BigDecimal resolutionAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", length = 20, nullable = false)
    private DisputeStatus status = DisputeStatus.Open;

    // FK -> Users(UserId)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ResolvedBy")
    private User resolvedBy;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @Column(name = "ResolvedAt")
    private LocalDateTime resolvedAt;

    @PreUpdate
    public void onUpdate() {
        // Nếu muốn tự set ResolvedAt khi status chuyển sang RESOLVED/CLOSED
        if ((status == DisputeStatus.Resolved || status == DisputeStatus.Rejected) && resolvedAt == null) {
            resolvedAt = LocalDateTime.now();
        }
    }
}
