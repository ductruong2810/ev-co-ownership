package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Voting")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Voting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VotingId")
    private Long votingId;

    @Column(name = "GroupId")
    private Long groupId;

    @Column(name = "Title")
    private String title;

    @Column(name = "Description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "VotingType", length = 50)
    private String votingType;

    @Column(name = "Options", columnDefinition = "TEXT")
    private String options;

    @Column(name = "Results", columnDefinition = "TEXT")
    private String results;

    @Column(name = "Deadline")
    private LocalDateTime deadline;

    @Column(name = "Status", nullable = false, length = 20)
    private String status;

    @Column(name = "CreatedBy")
    private Long createdBy;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "RelatedExpenseId")
    private Long relatedExpenseId;

    @Column(name = "EstimatedAmount", precision = 12, scale = 2)
    private BigDecimal estimatedAmount;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = "ACTIVE";
        }
    }
}
