package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "GroupMember")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "GroupMemberId")
    private Long groupMemberId;

    @Column(name = "GroupId", nullable = false)
    private Long groupId;

    @Column(name = "UserId", nullable = false)
    private Long userId;

    @Column(name = "Role")
    private String role;
}
