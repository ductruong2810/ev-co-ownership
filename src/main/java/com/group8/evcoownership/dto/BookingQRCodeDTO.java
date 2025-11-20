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
    //private String qrCode;
    @JsonRawValue
    private String qrCodeCheckin;
    @JsonRawValue
    private String qrCodeCheckout;
    private String startDateTime;
    private String endDateTime;
    private String createdAt;
}
