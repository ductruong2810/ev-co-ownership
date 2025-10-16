package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.PaymentStatus;
import com.group8.evcoownership.enums.PaymentType;
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
    @JoinColumn(name = "PayerUserId", nullable = false)
    private User payer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "FundId", nullable = false)
    private SharedFund fund;

    @NotNull
    @Column(name = "Amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "PaymentDate")
    private LocalDateTime paymentDate;

    @Size(max = 50)
    @Column(name = "PaymentMethod", length = 50)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", length = 20)
    private PaymentStatus status;

    @Size(max = 100)
    @Nationalized
    @Column(name = "TransactionCode", length = 100)
    private String transactionCode;

    @Nationalized
    @Lob
    @Column(name = "ProviderResponse")
    private String providerResponse;

    @Enumerated(EnumType.STRING)
    @Column(name = "PaymentType", length = 20, nullable = false)
    private PaymentType paymentType;

    @Column(name = "PaymentCategory", length = 20)
    private String paymentCategory; // GROUP or PERSONAL

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ChargedUserId")
    private User chargedUser; // required when PERSONAL

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SourceDisputeId")
    private Dispute sourceDispute; // if originated from a dispute

    @Lob
    @Nationalized
    @Column(name = "PersonalReason")
    private String personalReason;


    @Version
    @Column(name = "Version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    public void prePersist() {
        if (version == null) version = 0L;
    }
}
