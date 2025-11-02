package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupBookingDTO {
    private Long groupId;
    private String groupName;
    private List<BookingQRCodeDTO> bookings;
}
