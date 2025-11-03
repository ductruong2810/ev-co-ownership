package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingQRCodeDTO {
    private Long bookingId;
//    private String qrCode;
    private String qrCodeCheckin;
    private String qrCodeCheckout;
    private String startDateTime;
    private String endDateTime;
    private String createdAt;
}
