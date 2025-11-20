package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginResponseDTO {
    private String accessToken;
//    private String refreshToken;
    private String role;
    //bo sung de nhan biet staff, coowner, admin, technician
}
