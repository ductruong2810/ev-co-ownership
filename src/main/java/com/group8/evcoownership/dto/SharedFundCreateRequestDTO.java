package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SharedFundCreateRequestDTO {
    @NotNull
    Long groupId;

    @PositiveOrZero
    BigDecimal targetAmount;
}
