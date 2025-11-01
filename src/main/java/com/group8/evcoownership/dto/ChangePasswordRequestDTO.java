package com.group8.evcoownership.dto;

import com.group8.evcoownership.validation.PasswordConfirmation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@PasswordConfirmation(
        passwordField = "newPassword",
        confirmPasswordField = "confirmPassword",
        message = "New password and confirm password do not match!!"
)
public class ChangePasswordRequestDTO {

    @NotBlank(message = "Old password cannot be blank!!")
    private String oldPassword;

    @NotBlank(message = "New password cannot be blank!!")
    @Size(min = 8, max = 50, message = "Password must be 8-50 characters")
    @Pattern(
            regexp = "^(?!.*[<>])(?!.*(?i)script)(?!.*(?i)javascript)(?!.*(?i)onerror)(?!.*(?i)onload)[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,./?~`]+$",
            message = "Password must be 5-50 characters, at least 1 uppercase letter and 1 special character"
    )
    private String newPassword;

    @NotBlank(message = "Confirm password cannot be blank!!")
    private String confirmPassword;
}
