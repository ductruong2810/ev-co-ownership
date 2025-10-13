package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.RoleName;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "Roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RoleId", nullable = false)
    private Long roleID;

    @Enumerated(EnumType.STRING)
    @Column(name = "RoleName", length = 30, nullable = false, unique = true)
    private RoleName roleName;

    // Relationships với User
    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    private List<User> users;
}