package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequestDTO {

    @NotNull(message = "userId is required")
    private Long userId;

    @Size(max = 100, message = "Full name must not exceed 100 characters")
    @Pattern(regexp = "^[\\p{L}\\s]+$", message = "Full name must contain only letters and spaces")
    private String fullName;

    @Pattern(regexp = "^0\\d{9}$", message = "Phone number must be 10 digits starting with 0")
    private String phoneNumber;
}
