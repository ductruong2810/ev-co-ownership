package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationErrorResponseDTO {
    private int status;
    private String error;
    private String message;
    private String path;
    private List<FieldError> errors;

    @Data
    @Builder
    public static class FieldError {
        private String field;
        private String message;
    }

    // Helper method: tạo response với 1 lỗi duy nhất
    public static ValidationErrorResponseDTO singleError(
            int status, String error, String message, String field, String path) {
        return ValidationErrorResponseDTO.builder()
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .errors(List.of(FieldError.builder().field(field).message(message).build()))
                .build();
    }
}
