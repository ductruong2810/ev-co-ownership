package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class VotingResponseDTO {
    private Long votingId;
    private Long groupId;
    private String title;
    private String description;
    private String votingType;
    private String status;
    private LocalDateTime deadline;
    private BigDecimal estimatedAmount;
    private Long relatedExpenseId;
    private Map<String, Object> options;
    private Map<String, Object> results;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private Boolean hasVoted;
    private String userVote;
    private Integer totalVotes;
    private Integer totalMembers;
    private String votingProgress;
    private String timeRemaining;
}
