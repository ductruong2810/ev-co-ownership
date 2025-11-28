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

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "FundId", nullable = true)
    private SharedFund fund;

    @NotNull
    @Column(name = "Amount", nullable = false, precision = 18, scale = 2)
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
    @Column(name = "TransactionCode", length = 100)
    private String transactionCode;

    @Column(name = "ProviderResponse", columnDefinition = "TEXT")
    private String providerResponse;

    @Enumerated(EnumType.STRING)
    @Column(name = "PaymentType", length = 20, nullable = false)
    private PaymentType paymentType;

    @Column(name = "PaymentCategory", length = 20)
    private String paymentCategory; // GROUP or PERSONAL

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ChargedUserId")
    private User chargedUser; // required when PERSONAL

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "SourceDisputeId")
//    private Dispute sourceDispute; // if originated from a dispute

    @Column(name = "PersonalReason", columnDefinition = "TEXT")
    private String personalReason;

    // Payment.java

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "MaintenanceId")
    private Maintenance maintenance;
// - Đây là link NGƯỢC từ Payment -> Maintenance.
// - Chỉ những payment nào thuộc loại MAINTENANCE_FEE mới set field này (maintenance != null).
// - Các payment khác (DEPOSIT, CONTRIBUTION, v.v.) sẽ để maintenance = null.


    @Version
    @Column(name = "Version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "PaidAt")
    private LocalDateTime paidAt;

    @PrePersist
    public void prePersist() {
        this.paymentDate = LocalDateTime.now();
        if (version == null) version = 0L;
    }
}
