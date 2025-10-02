package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.VotingStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "Voting")
@Builder
public class Voting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VotingID")
    private Long votingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GroupID", nullable = false)
    private OwnershipGroup group;

    @Column(name = "Title")
    private String title;

    @Column(name = "Description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "Deadline")
    private LocalDateTime deadline;

    @Column(name = "Status", length = 20)
    private VotingStatus status;  // default

    // Quan hệ 1-nhìu với VotingOption
    @OneToMany(mappedBy = "voting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VotingOption> options;

    // Quan hệ 1-nhìu với VotingResult
    @OneToMany(mappedBy = "voting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VotingResult> results;
}
