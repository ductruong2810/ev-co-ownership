package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.FeedbackAdminAction;
import com.group8.evcoownership.enums.MemberFeedbackStatus;
import com.group8.evcoownership.enums.ReactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private MemberFeedbackStatus status; // PENDING (DISAGREE), APPROVED (AGREE hoặc Admin approve), REJECTED (Admin reject)

    @Enumerated(EnumType.STRING)
    @Column(name = "ReactionType")
    private ReactionType reactionType; // AGREE → APPROVED, DISAGREE → PENDING

    @Column(name = "Reason", length = 1000)
    private String reason; // Lý do (bắt buộc nếu reactionType = DISAGREE)

    @Column(name = "AdminNote", length = 1000)
    private String adminNote; // Ghi chú từ admin khi xử lý feedback

    @Enumerated(EnumType.STRING)
    @Column(name = "LastAdminAction", length = 50)
    private FeedbackAdminAction lastAdminAction; // Hành động gần nhất của admin đối với feedback

    @Column(name = "LastAdminActionAt")
    private LocalDateTime lastAdminActionAt; // Thời điểm admin xử lý gần nhất

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

