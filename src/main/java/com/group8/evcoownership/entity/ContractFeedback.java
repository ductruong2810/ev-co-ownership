package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.MemberFeedbackStatus;
import com.group8.evcoownership.enums.ReactionType;
import com.group8.evcoownership.enums.FeedbackAdminAction;
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
    private MemberFeedbackStatus status; // PENDING (DISAGREE), ACCEPTED (AGREE), APPROVED, REJECTED

    @Enumerated(EnumType.STRING)
    @Column(name = "ReactionType")
    private ReactionType reactionType; // AGREE → ACCEPTED, DISAGREE → PENDING

    @Nationalized
    @Column(name = "Reason", length = 1000)
    private String reason; // Lý do (bắt buộc nếu reactionType = DISAGREE)

    @Column(name = "IsProcessed", nullable = false, columnDefinition = "bit default 0")
    @Builder.Default
    private Boolean isProcessed = false; // Đã được admin xử lý (approve/reject) chưa

    @Column(name = "ApproveCount", nullable = false, columnDefinition = "int default 0")
    @Builder.Default
    private Integer approveCount = 0; // Số lần feedback đã được admin chấp nhận và chỉnh sửa contract

    @Column(name = "RejectCount", nullable = false, columnDefinition = "int default 0")
    @Builder.Default
    private Integer rejectCount = 0; // Số lần feedback bị admin từ chối

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

