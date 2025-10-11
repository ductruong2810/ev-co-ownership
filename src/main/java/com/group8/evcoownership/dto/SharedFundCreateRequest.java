package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SharedFundCreateRequest {
    @NotNull
    Long groupId;

}
