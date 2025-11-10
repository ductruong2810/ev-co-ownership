package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "VoteRecord", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_VoteRecord", columnNames = {"VotingId", "UserId"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VoteRecordId")
    private Long voteRecordId;

    @Column(name = "VotingId", nullable = false)
    private Long votingId;

    @Column(name = "UserId", nullable = false)
    private Long userId;

    @Column(name = "SelectedOption", nullable = false, length = 50)
    private String selectedOption;

    @Column(name = "VotedAt", nullable = false)
    private LocalDateTime votedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VotingId", insertable = false, updatable = false)
    private Voting voting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserId", insertable = false, updatable = false)
    private User user;

    @PrePersist
    protected void onCreate() {
        votedAt = LocalDateTime.now();
    }
}
