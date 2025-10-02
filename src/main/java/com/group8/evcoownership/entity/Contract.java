package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "Contract")
@Builder

public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ContractID")
    private Long contractID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GroupID", nullable = false)
    private OwnershipGroup group;

    @Column(name = "StartDate")
    private LocalDate startDate;

    @Column(name = "EndDate")
    private LocalDate endDate;

    @Column(name = "Terms", columnDefinition = "NVARCHAR(MAX)")
    private String terms;

    @Column(name = "InitialFee", precision = 15, scale = 2)
    private BigDecimal initialFee;

    @Column(name = "PenaltyRate", precision = 5, scale = 2)
    private BigDecimal penaltyRate;

    @Column(name = "CreatedAt", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt", insertable = false, updatable = false)
    private LocalDateTime updatedAt;





}
