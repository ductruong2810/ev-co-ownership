package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "VotingResult")
@Builder

public class VotingResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ResultID")
    private Integer resultId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VotingID", nullable = false)
    private Voting voting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "OptionID", nullable = false)
    private VotingOption option;

    @Column(name = "VotedAt", insertable = false, updatable = false)
    private LocalDateTime votedAt;
}
