package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingQRCodeDTO {
    private Long bookingId;
    private String qrCode;
    private String startDateTime;
    private String endDateTime;
}
