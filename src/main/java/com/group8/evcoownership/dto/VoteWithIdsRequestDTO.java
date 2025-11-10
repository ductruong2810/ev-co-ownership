package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VoteWithIdsRequestDTO {
    @NotNull(message = "Group ID is required")
    private Long groupId;

    @NotNull(message = "Voting ID is required")
    private Long votingId;

    @NotBlank(message = "Selected option is required")
    private String selectedOption;
}
