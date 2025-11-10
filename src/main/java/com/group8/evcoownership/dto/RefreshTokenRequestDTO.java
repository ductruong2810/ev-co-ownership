package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequestDTO {

    @NotBlank(message = "Refresh token cannot be blank")
    private String refreshToken;
}
