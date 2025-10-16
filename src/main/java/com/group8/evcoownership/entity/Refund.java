package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Refund")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Refund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RefundId", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DisputeId", nullable = false)
    private Dispute dispute;

    @Column(name = "Amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "Method", length = 30, nullable = false)
    private String method;

    @Column(name = "TxnRef", length = 100)
    private String txnRef;

    @Column(name = "Status", length = 20, nullable = false)
    private String status; // PENDING/SUCCESS/FAILED

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "SettledAt")
    private LocalDateTime settledAt;

    @Lob
    @Column(name = "Note")
    private String note;

    @Column(name = "Provider", length = 20)
    private String provider;

    @Column(name = "ProviderTxnRef", length = 100)
    private String providerTxnRef;

    @Column(name = "ProviderRefundRef", length = 100)
    private String providerRefundRef;

    @Column(name = "ReasonCode", length = 20)
    private String reasonCode;

    @Column(name = "Channel", length = 20)
    private String channel;

    @Lob
    @Column(name = "RawResponse")
    private String rawResponse;
}


