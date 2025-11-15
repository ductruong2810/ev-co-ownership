package com.group8.evcoownership.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateVotingWithGroupRequestDTO {
    @NotNull(message = "Group ID is required")
    private Long groupId;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "Voting type is required")
    private String votingType;

    @NotNull(message = "Options are required")
    @Size(min = 2, message = "At least 2 options required")
    private List<CreateVotingRequestDTO.VotingOption> options;

    @NotNull(message = "Deadline is required")
    @Future(message = "Deadline must be in the future")
    private LocalDateTime deadline;

    private BigDecimal estimatedAmount;

    private Long relatedExpenseId;
}
