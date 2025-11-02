package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupBookingsResponseDTO {
    private Long userId;
    private String fullName;
    private List<GroupBookingDTO> groups;
}
