package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.GroupRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "OwnershipShare")
@Builder

public class OwnershipShare {

    @EmbeddedId
    private OwnershipShareId id;
    //đây là khoá kết hợp giữa UserId và GroupId
    @ManyToOne
    @MapsId("userId")
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @ManyToOne
    @MapsId("groupId")
    @JoinColumn(name = "GroupID", nullable = false)
    private OwnershipGroup group;

    @Enumerated(EnumType.STRING)
    @Column(name = "GroupRole",length = 20)
    private GroupRole groupRole;

    @CreationTimestamp
    @Column(name = "JoinDate", updatable = false, nullable = false)
    private LocalDateTime joinDate;

    @Column(name = "OwnershipPercentage", precision = 5, scale = 2)
    private BigDecimal ownerShipPercentage;
    //Dung double la cut

    @Column(name = "UpdatedAt", insertable = false)
    private LocalDateTime updatedAt;
}

