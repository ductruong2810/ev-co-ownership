package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.GroupRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "OwnershipShare")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnershipShare {
    @EmbeddedId
    private OwnershipShareId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserId", nullable = false)
    private User user;

    @MapsId("groupId") // ánh xạ phần groupId trong EmbeddedId
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "GroupId", nullable = false)
    private OwnershipGroup group;

    @Enumerated(EnumType.STRING)
    @Column(name = "GroupRole", length = 50)
    private GroupRole groupRole;

    @Column(name = "JoinDate")
    private LocalDateTime joinDate;

    @Column(name = "OwnershipPercentage", precision = 5, scale = 2)
    private BigDecimal ownershipPercentage;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        if (groupRole == null) groupRole = GroupRole.Member;
        if (joinDate == null) joinDate = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}