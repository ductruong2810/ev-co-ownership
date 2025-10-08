package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Payment")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PaymentID")
    private Long paymentId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "FundID", nullable = false)
    private SharedFund fund;

    @ManyToOne(optional = false)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @Column(name = "Amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "PaymentDate")
    private LocalDateTime paymentDate;

    @Column(name = "PaymentMethod", length = 50)
    private String paymentMethod; // ví dụ: VNPay, Bank

    @Column(name = "Status", length = 20)
    private String status; // Pending|Success|Failed (theo DB hiện tại)

    @Column(name = "TransactionCode", length = 100)
    private String transactionCode;

    @Lob
    @Column(name = "ProviderResponse")
    private String providerResponse;

    @Column(name = "PaymentType", length = 20)
    private String paymentType; // Initial|TopUp|Penalty|Other
}
