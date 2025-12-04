package com.group8.evcoownership.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangeEmailRequestDTO {

    @NotBlank(message = "New email cannot be blank")
    @Email(message = "New email is not valid")
    private String newEmail;
}


