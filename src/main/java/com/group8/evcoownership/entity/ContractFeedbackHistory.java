package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.FeedbackAdminAction;
import com.group8.evcoownership.enums.FeedbackHistoryAction;
import com.group8.evcoownership.enums.MemberFeedbackStatus;
import com.group8.evcoownership.enums.ReactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ContractFeedbackHistory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContractFeedbackHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "HistoryId", nullable = false)
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FeedbackId", nullable = true)  // Đổi từ nullable = false thành nullable = true
    private ContractFeedback feedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ContractId", nullable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserId", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", length = 32, nullable = false)
    private MemberFeedbackStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "ReactionType", length = 16)
    private ReactionType reactionType;

    @Column(name = "Reason", length = 1000)
    private String reason;

    @Column(name = "AdminNote", length = 1000)
    private String adminNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "LastAdminAction", length = 32)
    private FeedbackAdminAction lastAdminAction;

    @Column(name = "LastAdminActionAt")
    private LocalDateTime lastAdminActionAt;

    @Column(name = "SubmittedAt")
    private LocalDateTime submittedAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "HistoryAction", length = 32, nullable = false)
    private FeedbackHistoryAction historyAction;

    @Column(name = "ActionNote", length = 1000)
    private String actionNote;

    @Column(name = "ArchivedAt", nullable = false)
    private LocalDateTime archivedAt;

    @PrePersist
    public void onCreate() {
        if (archivedAt == null) {
            archivedAt = LocalDateTime.now();
        }
    }
}