package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Expense")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ExpenseID")
    private Long expenseId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "FundID", nullable = false)
    private SharedFund fund;

    @ManyToOne
    @JoinColumn(name = "IncidentID")
    private Incident incident; // có thể null

    @Lob
    @Column(name = "Description")
    private String description;

    @Column(name = "Amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "ExpenseDate")
    private LocalDateTime expenseDate;
}
