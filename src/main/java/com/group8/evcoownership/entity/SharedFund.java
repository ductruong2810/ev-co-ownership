package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "SharedFund")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SharedFund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FundID")
    private Long fundId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "GroupID", nullable = false)
    private OwnershipGroup group;

    @Column(name = "Balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;
}
