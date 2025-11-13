package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdatePhoneRequestDTO {

    @NotNull(message = "userId is required")
    private Long userId;

    @Pattern(regexp = "^0\\d{9}$", message = "Phone number must be 10 digits starting with 0")
    private String phoneNumber;
}
