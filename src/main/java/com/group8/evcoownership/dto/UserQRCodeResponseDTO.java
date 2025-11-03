package com.group8.evcoownership.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserQRCodeResponseDTO {
    private Long userId;
    private String userName;
    private Long groupId;
    private Long bookingId;

    @JsonRawValue
    private String qrCode;
}
