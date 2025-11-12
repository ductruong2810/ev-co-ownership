package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.MemberFeedbackStatus;
import com.group8.evcoownership.enums.ReactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Entity
@Table(name = "ContractFeedback")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FeedbackId", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ContractId", nullable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserId", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false)
    private MemberFeedbackStatus status; // PENDING, APPROVED, REJECTED

    @Enumerated(EnumType.STRING)
    @Column(name = "ReactionType")
    private ReactionType reactionType; // AGREE, DISAGREE (có khi status = PENDING, APPROVED, hoặc REJECTED)

    @Nationalized
    @Column(name = "Reason", length = 1000)
    private String reason; // Lý do (bắt buộc nếu reactionType = DISAGREE)

    @Column(name = "SubmittedAt", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        submittedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

