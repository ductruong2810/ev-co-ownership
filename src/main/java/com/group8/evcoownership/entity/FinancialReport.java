package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "FinancialReport")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class FinancialReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ReportID")
    private Long reportId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "FundID", nullable = false)
    private SharedFund fund;

    @Column(name = "ReportMonth")
    private Integer reportMonth;

    @Column(name = "ReportYear")
    private Integer reportYear;

    @Column(name = "TotalIncome", precision = 15, scale = 2)
    private BigDecimal totalIncome;

    @Column(name = "TotalExpense", precision = 15, scale = 2)
    private BigDecimal totalExpense;

    @ManyToOne(optional = false)
    @JoinColumn(name = "GeneratedBy", nullable = false)
    private User generatedBy;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;
}
