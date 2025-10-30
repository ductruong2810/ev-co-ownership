package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.Nationalized;

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

    // FK -> SharedFund
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "FundId", nullable = false)
    private SharedFund fund;

    @NotNull
    @Column(name = "SourceType", length = 30, nullable = false)
    private String sourceType; // 'INCIDENT' hoặc 'MAINTENANCE'

    @NotNull
    @Column(name = "SourceId", nullable = false)
    private Long sourceId; // ID logic đến Incident/Maintenance

    // FK -> Users (người được hoàn tiền, nếu có)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RecipientUserId")
    private User recipientUser;

    @Nationalized
    @Lob
    @Column(name = "Description")
    private String description;

    @NotNull
    @Column(name = "Amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "Status", length = 20, nullable = false)
    private String status; // PENDING | COMPLETED

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "ExpenseDate")
    private LocalDateTime expenseDate;

    // FK -> Users (admin duyệt)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ApprovedBy")
    private User approvedBy;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
