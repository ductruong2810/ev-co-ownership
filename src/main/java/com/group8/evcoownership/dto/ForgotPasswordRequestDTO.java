package com.group8.evcoownership.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequestDTO {

    @NotBlank(message = "Email cannot be blank!!")
    @Email(message = "Invalid email!!")
    private String email;
}
