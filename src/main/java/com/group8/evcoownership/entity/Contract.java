package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.ContractApprovalStatus;
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

    // Template sẽ được xử lý ở Frontend, không cần lưu trong database
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "TemplateId")
    // private ContractTemplate template;

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

    // Contract Approval Fields
    @Enumerated(EnumType.STRING)
    @Column(name = "ApprovalStatus")
    @ColumnDefault("'PENDING'")
    private ContractApprovalStatus approvalStatus = ContractApprovalStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ApprovedBy")
    private User approvedBy;

    @Column(name = "ApprovedAt")
    private LocalDateTime approvedAt;

    @Nationalized
    @Column(name = "RejectionReason", length = 500)
    private String rejectionReason;

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
