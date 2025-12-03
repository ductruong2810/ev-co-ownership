package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.AuditLogListResponse;
import com.group8.evcoownership.dto.AuditLogRequest;
import com.group8.evcoownership.dto.AuditLogResponse;
import com.group8.evcoownership.entity.AuditLog;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.repository.AuditLogRepository;
import com.group8.evcoownership.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /**
     * Create an audit log entry
     */
    public void createAuditLog(AuditLogRequest request) {
        try {
            // Get current authenticated user
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Long userId = null;
            if (authentication != null && authentication.getPrincipal() instanceof String) {
                try {
                    userId = Long.parseLong(authentication.getPrincipal().toString());
                } catch (NumberFormatException e) {
                    log.warn("Could not parse userId from authentication principal: {}", authentication.getPrincipal());
                }
            }

            User user = userId != null ? userRepository.findById(userId).orElse(null) : null;

            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .actionType(request.getType())
                    .entityType(request.getEntityType())
                    .entityId(request.getEntityId() != null ? request.getEntityId().toString() : null)
                    .message(request.getMessage())
                    .metadata(request.getMetadata())
                    .createdAt(LocalDateTime.now())
                    .build();

            auditLogRepository.save(auditLog);
            log.info("Audit log created: {} by user {}", request.getType(), userId);
        } catch (Exception e) {
            log.error("Failed to create audit log: {}", e.getMessage(), e);
            // Don't throw exception to avoid breaking the main flow
        }
    }

    /**
     * Get audit logs with filters and pagination
     */
    @Transactional(readOnly = true)
    public AuditLogListResponse getAuditLogs(
            Integer page,
            Integer size,
            Long userId,
            String actionType,
            String entityType,
            LocalDateTime from,
            LocalDateTime to,
            String search
    ) {
        // Default pagination
        if (page == null || page < 0) page = 0;
        if (size == null || size < 1) size = 20;
        if (size > 100) size = 100; // Limit max page size

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Normalize filter values
        String normalizedActionType = (actionType != null && !actionType.isEmpty() && !actionType.equals("ALL")) 
                ? actionType : null;
        String normalizedEntityType = (entityType != null && !entityType.isEmpty() && !entityType.equals("ALL")) 
                ? entityType : null;
        String normalizedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        Page<AuditLog> auditLogPage = auditLogRepository.findWithFilters(
                userId,
                normalizedActionType,
                normalizedEntityType,
                from,
                to,
                normalizedSearch,
                pageable
        );

        List<AuditLogResponse> logs = auditLogPage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return AuditLogListResponse.builder()
                .logs(logs)
                .total(auditLogPage.getTotalElements())
                .page(page)
                .pageSize(size)
                .build();
    }

    private AuditLogResponse mapToResponse(AuditLog auditLog) {
        String userRole = auditLog.getUser() != null && auditLog.getUser().getRole() != null
                ? auditLog.getUser().getRole().getRoleName().name()
                : "UNKNOWN";

        return AuditLogResponse.builder()
                .id(auditLog.getId())
                .timestamp(auditLog.getCreatedAt())
                .userId(auditLog.getUser() != null ? auditLog.getUser().getUserId() : null)
                .userName(auditLog.getUser() != null ? auditLog.getUser().getFullName() : "System")
                .userRole(userRole)
                .actionType(auditLog.getActionType())
                .entityType(auditLog.getEntityType())
                .entityId(auditLog.getEntityId())
                .message(auditLog.getMessage())
                .metadata(auditLog.getMetadata())
                .build();
    }
}

