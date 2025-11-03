package com.group8.evcoownership.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingQRCodeDTO {
    private Long bookingId;

    @JsonRawValue
    private String qrCode;

    private String startDateTime;
    private String endDateTime;
}
