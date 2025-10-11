package com.group8.evcoownership.dto;

import lombok.Data;

@Data
public class CancelBookingRequestDTO {
    private String reason;
    private boolean notifyUsers;
}
