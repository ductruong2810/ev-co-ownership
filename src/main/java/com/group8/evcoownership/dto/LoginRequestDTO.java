package com.group8.evcoownership.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequestDTO {

    @NotBlank(message = "Email cannot be blank!")
    @Email(message = "Invalid email!")
    private String email;

    @NotBlank(message = "Password cannot be blank!")
    private String password;

    //bổ sung field rememberme
    private boolean rememberMe = false;
}
