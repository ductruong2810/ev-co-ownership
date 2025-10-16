package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.DisputeStatus;
import com.group8.evcoownership.enums.DisputeType;
import com.group8.evcoownership.enums.RelatedEntityType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Nationalized;

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

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "FundId", nullable = false)
    private SharedFund fund;

    @Column(name = "CreatedBy")
    private Long createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "DisputeType", length = 50)
    private DisputeType disputeType; // FINANCIAL, USAGE, DECISION

    @Enumerated(EnumType.STRING)
    @Column(name = "RelatedEntityType", length = 50)
    private RelatedEntityType relatedEntityType; // INCIDENT, PAYMENT, EXPENSE, VOTING

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
    @Column(name = "Status", length = 20)
    private DisputeStatus status; // OPEN, IN_REVIEW, RESOLVED, CLOSED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ResolvedBy")
    private User resolvedBy;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "ResolvedAt")
    private LocalDateTime resolvedAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
