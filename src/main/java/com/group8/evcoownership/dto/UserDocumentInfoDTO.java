package com.group8.evcoownership.dto;

public record UserDocumentInfoDTO(
        String idNumber,
        String fullName,
        String dateOfBirth,
        String issueDate,
        String expiryDate,
        String address
) {
}
