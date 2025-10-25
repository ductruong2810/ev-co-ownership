package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.GroupStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "OwnershipGroup",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_OwnershipGroup_GroupName",
                columnNames = "GroupName"
        )
)
public class OwnershipGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "GroupId", nullable = false)
    private Long groupId;

    @Size(max = 100)
    @NotNull
    @Nationalized
    @Column(name = "GroupName", nullable = false, length = 100)
    private String groupName;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    private GroupStatus status; // default set in @PrePersist

    @Nationalized
    @Lob
    @Column(name = "Description")
    private String description;

    @Column(name = "MemberCapacity")
    private Integer memberCapacity;

    @Nationalized
    @Lob
    @Column(name = "RejectionReason")
    private String rejectionReason;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        final var now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = GroupStatus.PENDING;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
