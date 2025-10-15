package com.group8.evcoownership.entity;


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
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
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

    // CreatedBy hiện là BIGINT thuần (CHƯA FK)
    @Column(name = "CreatedBy")
    private Long createdBy;

    @Column(name = "DisputeType", length = 50)
    private String disputeType; // FINANCIAL, USAGE, DECISION...

    @Column(name = "RelatedEntityType", length = 50)
    private String relatedEntityType; // INCIDENT, PAYMENT, EXPENSE, VOTING...

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

    // Status NVARCHAR(20) DEFAULT 'OPEN' (để String cho khớp schema)
    @Column(name = "Status", length = 20)
    private String status = "OPEN";

    // FK -> Users(UserId)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ResolvedBy")
    private User resolvedBy;

    // DB đang default GETDATE(), nhưng thêm timestamp để đồng bộ 2 chiều
    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "ResolvedAt")
    private LocalDateTime resolvedAt;

    @PreUpdate
    public void touchResolvedAt() {
        if (resolvedAt == null && status != null) {
            String s = status.trim().toUpperCase();
            if ("RESOLVED".equals(s) || "CLOSED".equals(s)) {
                resolvedAt = LocalDateTime.now();
            }
        }
    }
}
