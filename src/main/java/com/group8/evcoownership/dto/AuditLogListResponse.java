package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogListResponse {
    private List<AuditLogResponse> logs;
    private Long total;
    private Integer page;
    private Integer pageSize;
}

