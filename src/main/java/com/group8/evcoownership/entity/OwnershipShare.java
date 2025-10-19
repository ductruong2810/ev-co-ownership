package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.DepositStatus;
import com.group8.evcoownership.enums.GroupRole;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "OwnershipShare")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnershipShare {

    @EmbeddedId
    private OwnershipShareId id;

    // FK -> Users(UserId)
    @MapsId("userId") // maps FK part of composite PK
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserId", nullable = false)
    private User user;

    // FK -> OwnershipGroup(GroupId)
    @MapsId("groupId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "GroupId", nullable = false)
    private OwnershipGroup group;

    @Enumerated(EnumType.STRING)
    @Column(name = "GroupRole", nullable = false, length = 50)
    private GroupRole groupRole; // default MEMBER

    @NotNull
    @DecimalMin(value = "0.01")
    @DecimalMax(value = "100.00")
    @Column(name = "OwnershipPercentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal ownershipPercentage;

    @Column(name = "JoinDate", nullable = false)
    private LocalDateTime joinDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "DepositStatus", nullable = false, length = 20)
    private DepositStatus depositStatus;

    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        final var now = LocalDateTime.now();
        if (groupRole == null) groupRole = GroupRole.MEMBER; // DB default too
        if (depositStatus == null) depositStatus = DepositStatus.PENDING; // Default deposit status
        if (joinDate == null) joinDate = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
