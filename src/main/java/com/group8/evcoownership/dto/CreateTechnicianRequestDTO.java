package com.group8.evcoownership.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for creating a Technician account from the Admin console.
 * Mirrors the fields used on the front-end Create Technician form.
 */
@Data
public class CreateTechnicianRequestDTO {

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must be at most 100 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email is invalid")
    @Size(max = 100, message = "Email must be at most 100 characters")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Size(max = 20, message = "Phone number must be at most 20 characters")
    private String phoneNumber;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 50, message = "Password must be between 8 and 50 characters")
    private String password;
}


