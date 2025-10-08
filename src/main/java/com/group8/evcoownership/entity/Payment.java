package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.PaymentStatus;
import com.group8.evcoownership.enums.PaymentType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PaymentId", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserId", nullable = false)
    private User user;

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
    @Column(name = "PaymentType", length = 20)
    private PaymentType paymentType;

}