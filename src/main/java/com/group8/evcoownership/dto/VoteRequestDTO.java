package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VoteRequestDTO {
    @NotBlank(message = "Selected option is required")
    private String selectedOption;
}
