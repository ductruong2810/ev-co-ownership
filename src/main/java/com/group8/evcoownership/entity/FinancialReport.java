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
@Table(name = "FinancialReport")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ReportId", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "FundId", nullable = false)
    private SharedFund fund;

    @Column(name = "ReportMonth")
    private Integer reportMonth;

    @Column(name = "ReportYear")
    private Integer reportYear;

    @Column(name = "TotalIncome", precision = 15, scale = 2)
    private BigDecimal totalIncome;

    @Column(name = "TotalExpense", precision = 15, scale = 2)
    private BigDecimal totalExpense;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "GeneratedBy", nullable = false)
    private User generatedBy;

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