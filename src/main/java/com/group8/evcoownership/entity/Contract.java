package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "Contract")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ContractId", nullable = false)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "GroupId", nullable = false)
    private OwnershipGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TemplateId")
    private ContractTemplate template;

    @Column(name = "StartDate")
    private LocalDate startDate;

    @Column(name = "EndDate")
    private LocalDate endDate;

    @Nationalized
    @Lob
    @Column(name = "Terms")
    private String terms;

    @Column(name = "RequiredDepositAmount", precision = 15, scale = 2)
    private BigDecimal requiredDepositAmount;

    @ColumnDefault("1")
    @Column(name = "IsActive")
    private Boolean isActive;

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
