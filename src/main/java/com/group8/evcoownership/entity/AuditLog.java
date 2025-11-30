package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "AuditLog")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AuditLogId", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserId")
    private User user;

    @Column(name = "ActionType", length = 50, nullable = false)
    private String actionType; // APPROVE, REJECT, CREATE, UPDATE, DELETE, REVIEW

    @Column(name = "EntityType", length = 50)
    private String entityType; // DOCUMENT, MAINTENANCE, VEHICLE_CHECK, GROUP, CONTRACT

    @Column(name = "EntityId", length = 100)
    private String entityId;

    @Column(name = "Message", columnDefinition = "TEXT", nullable = false)
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "Metadata", columnDefinition = "json")
    private Map<String, Object> metadata;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

