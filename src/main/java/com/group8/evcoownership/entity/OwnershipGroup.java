package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "OwnershipGroup")
@Builder

public class OwnershipGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)

    @Column(name = "GroupID")
    private Long groupId;

    @Column(name = "GroupName", nullable = false, length = 100)
    private String groupName;

    @Column(name = "CreatedAt", updatable = false, nullable = false, insertable = false)
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt", insertable = false)
    private LocalDateTime updatedAt;
}
