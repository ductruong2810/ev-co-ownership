package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    
    Page<AuditLog> findAll(Pageable pageable);
    
    @Query("SELECT a FROM AuditLog a WHERE a.user.userId = :userId")
    Page<AuditLog> findByUserId(@Param("userId") Long userId, Pageable pageable);
    
    Page<AuditLog> findByActionType(String actionType, Pageable pageable);
    
    Page<AuditLog> findByEntityType(String entityType, Pageable pageable);
    
    Page<AuditLog> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);
    
    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:userId IS NULL OR a.user.userId = :userId) AND " +
           "(:actionType IS NULL OR a.actionType = :actionType) AND " +
           "(:entityType IS NULL OR a.entityType = :entityType) AND " +
           "(:from IS NULL OR a.createdAt >= :from) AND " +
           "(:to IS NULL OR a.createdAt <= :to) AND " +
           "(:search IS NULL OR " +
           "LOWER(a.user.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.actionType) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<AuditLog> findWithFilters(
            @Param("userId") Long userId,
            @Param("actionType") String actionType,
            @Param("entityType") String entityType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("search") String search,
            Pageable pageable
    );
}

