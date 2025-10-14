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
import java.util.List;

@Entity
@Table(name = "OwnershipGroup")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
    @Column(name = "Status", length = 20)
    private GroupStatus status;

    @Nationalized
    @Lob
    @Column(name = "Description")
    private String description;

    @OneToMany(mappedBy = "group", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
    private List<OwnershipShare> ownershipShares;

    @OneToOne(mappedBy = "ownershipGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    private Vehicle vehicle;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    // Chỉ giữ lại những relationships quan trọng
    @OneToOne(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private Contract contract;

    @OneToOne(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private SharedFund sharedFund;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}