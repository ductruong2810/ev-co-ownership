package com.group8.evcoownership.entity;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FundId")
    private SharedFund fund;

    @Column(name = "SourceType", length = 20)
    private String sourceType; // "MAINTENANCE", "VEHICLE_REPORT", "INCIDENT"

    @Column(name = "SourceId")
    private Long sourceId; // ID của source entity

    @Nationalized
    @Lob
    @Column(name = "Description")
    private String description;

    @NotNull
    @Column(name = "Amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "ExpenseDate")
    private LocalDateTime expenseDate;
}