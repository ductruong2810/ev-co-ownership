package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequestDTO {

    @NotBlank(message = "Refresh token không được để trống")
    private String refreshToken;
}
