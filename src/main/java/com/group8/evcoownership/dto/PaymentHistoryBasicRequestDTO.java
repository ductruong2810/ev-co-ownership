package com.group8.evcoownership.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

// PaymentHistoryBasicRequestDTO.java
@Data
public class PaymentHistoryBasicRequestDTO {
    @NotNull
    private Long userId;
    @NotNull private Long groupId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    @Min(0) private Integer page = 0;
    @Min(1) private Integer size = 20;

    public LocalDateTime getFromAt(){ return fromDate==null?null:fromDate.atStartOfDay(); }
    public LocalDateTime getToAt(){ return toDate==null?null:toDate.atTime(23,59,59); }
}
