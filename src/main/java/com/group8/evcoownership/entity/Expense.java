package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Expense")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ExpenseId", nullable = false)
    private Long id;

    // FK → SharedFund
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "FundId", nullable = false)
    private SharedFund fund;

    @NotNull
    @Column(name = "SourceType", length = 30, nullable = false)
    private String sourceType;  // 'MAINTENANCE' | 'INCIDENT'

    @NotNull
    @Column(name = "SourceId", nullable = false)
    private Long sourceId;      // ID của Maintenance hoặc Incident

    // FK → Users (người được hoàn tiền nếu có)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RecipientUserId")
    private User recipientUser;

    @Column(name = "Description", columnDefinition = "TEXT")
    private String description;

    @NotNull
    @Column(name = "Amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "Status", length = 20, nullable = false)
    private String status; // PENDING | COMPLETED

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "ExpenseDate")
    private LocalDateTime expenseDate; // thời điểm chi thực tế

    // FK → Users (staff/admin duyệt)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ApprovedBy")
    private User approvedBy;

    // Số dư quỹ sau khi chi (snapshot)
    @Column(name = "FundBalanceAfter", precision = 15, scale = 2)
    private BigDecimal fundBalanceAfter;

    // ====== Lifecycle Hooks ======
    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
