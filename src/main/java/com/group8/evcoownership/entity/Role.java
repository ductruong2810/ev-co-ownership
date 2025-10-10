package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.RoleName;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}