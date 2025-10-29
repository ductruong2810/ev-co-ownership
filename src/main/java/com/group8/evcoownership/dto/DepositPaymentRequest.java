package com.group8.evcoownership.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

@Builder
@Schema(description = "Request để tạo payment cho tiền cọc")
public record DepositPaymentRequest(

        @NotNull(message = "userId is required")
        @Pattern(regexp = "\\d+", message = "userId must be a valid number")
        @Schema(description = "ID của user", example = "1")
        String userId,


        @NotNull(message = "groupId is required")
        @Pattern(regexp = "\\d+", message = "groupId must be a valid number")
        @Schema(description = "ID của group", example = "5")
        String groupId
//
//        @NotNull(message = "amount is required")
//        @DecimalMin(value = "0.01", message = "amount must be greater than 0")
//        @Schema(description = "Số tiền cọc", example = "5000000")
//        BigDecimal amount,
//
//        @NotNull(message = "paymentMethod is required")
//        @Schema(description = "Phương thức thanh toán", example = "VNPAY")
//        String paymentMethod
) {
}
