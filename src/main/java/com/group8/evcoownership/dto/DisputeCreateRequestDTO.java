package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.DisputeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeCreateRequestDTO {

    @NotNull(message = "GroupId is required")
    private Long groupId;

    @NotNull(message = "DisputeType is required")
    private DisputeType disputeType;

    @NotBlank(message = "Title is required")
    private String title;

    private String description; // optional
}


