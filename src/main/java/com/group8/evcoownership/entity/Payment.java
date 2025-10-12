package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Payment")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PaymentId", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserId", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "FundID", nullable = false)
    private SharedFund fund;

    @NotNull
    @Column(name = "Amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "PaymentDate")
    private LocalDateTime paymentDate;

    @Size(max = 50)
    @Column(name = "PaymentMethod", length = 50)
    private String paymentMethod;

    @Column(name = "Status", length = 20)
    private String status; // PENDING, COMPLETED, FAILED, CANCELLED

    @Size(max = 100)
    @Nationalized
    @Column(name = "TransactionCode", length = 100)
    private String transactionCode;

    @Nationalized
    @Lob
    @Column(name = "ProviderResponse")
    private String providerResponse;

    @Column(name = "PaymentType", length = 20)
    private PaymentType paymentType;

    @Version
    @Column(name = "Version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    public void prePersist() {
        if (version == null) version = 0L;
    }
}
