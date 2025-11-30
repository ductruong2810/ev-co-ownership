package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogResponse {
    private Long id;
    private LocalDateTime timestamp;
    private Long userId;
    private String userName;
    private String userRole;
    private String actionType;
    private String entityType;
    private String entityId;
    private String message;
    private Map<String, Object> metadata;
}

