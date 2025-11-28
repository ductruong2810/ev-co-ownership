package com.group8.evcoownership.entity;


import com.group8.evcoownership.enums.InvitationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "Invitation",
        indexes = {
                @Index(name = "IX_Invitation_ExpiresAt", columnList = "ExpiresAt"),
                @Index(name = "IX_Invitation_Group_ExpiresAt", columnList = "GroupId,ExpiresAt")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "UQ_Invitation_Token", columnNames = "Token")
        })
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "InvitationId", nullable = false)
    private Long invitationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "GroupId", nullable = false)
    private OwnershipGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "InviterUserId", nullable = false)
    private User inviter;

    @Column(name = "InviteeEmail", length = 100, nullable = false)
    private String inviteeEmail;

    @Column(name = "Token", length = 128, nullable = false, unique = true)
    private String token;

    @Column(name = "OtpCode", length = 6, nullable = false)
    private String otpCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", length = 20, nullable = false)
    private InvitationStatus status;

    @Column(name = "SuggestedPercentage", precision = 5, scale = 2)
    private BigDecimal suggestedPercentage; // optional

    @Column(name = "ExpiresAt", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "ResendCount", nullable = false)
    private Integer resendCount;

    @Column(name = "LastSentAt")
    private LocalDateTime lastSentAt;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "AcceptedAt")
    private LocalDateTime acceptedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "AcceptedBy")
    private User acceptedBy;

    @PrePersist
    void onCreate() {
        final var now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (resendCount == null) resendCount = 0;
        if (status == null) status = InvitationStatus.PENDING;
    }
}

