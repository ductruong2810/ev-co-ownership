package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "SharedFund")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SharedFund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FundId", nullable = false)
    private Long fundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GroupId", nullable = false)
    private OwnershipGroup group;

    @Column(name = "Balance", precision = 15, scale = 2)
    private BigDecimal balance;

    @Column(name = "TargetAmount", precision = 15, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "CreatedAt", updatable = false, insertable = false)
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt", insertable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "Version")
    private Long version;

    // Relationships với các entity khác
    @OneToMany(mappedBy = "fund", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Dispute> disputes;

    @PrePersist
    public void onCreate() {
        if (balance == null) balance = BigDecimal.ZERO;
        if (targetAmount == null) targetAmount = BigDecimal.ZERO;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
