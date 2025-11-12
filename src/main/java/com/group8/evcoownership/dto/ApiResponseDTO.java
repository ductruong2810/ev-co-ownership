package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponseDTO<T> {
    private Boolean success;
    private String message;
    private T data;
}

