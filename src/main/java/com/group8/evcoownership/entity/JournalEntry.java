package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "JournalEntry")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EntryId", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DisputeId", nullable = false)
    private Dispute dispute;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "FundId", nullable = false)
    private SharedFund fund;

    @Column(name = "AccountCode", length = 50, nullable = false)
    private String accountCode;

    @Column(name = "Debit", precision = 15, scale = 2, nullable = false)
    private BigDecimal debit;

    @Column(name = "Credit", precision = 15, scale = 2, nullable = false)
    private BigDecimal credit;

    @Column(name = "Memo", length = 255)
    private String memo;

    @Column(name = "PostedAt")
    private LocalDateTime postedAt;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;
}


