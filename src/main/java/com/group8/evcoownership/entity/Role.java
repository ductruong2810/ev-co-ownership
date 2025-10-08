package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.RoleName;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Role")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RoleId", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "RoleName", length = 30, nullable = false, unique = true)
    private RoleName roleName;

}