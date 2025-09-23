package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.Role;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "UserID")
    private Integer userId;

    @Column(name = "FullName", nullable = false)
    private String fullName;

    @Column(name = "Email", nullable = false, unique = true)
    private String email;

    @Column(name = "PasswordHash", nullable = false)
    private String passwordHash;

    @Column(name = "PhoneNumber")
    private String phoneNumber;

    @Enumerated(EnumType.STRING)   // Hibernate sẽ lưu "CO_OWNER", "STAFF", "ADMIN" hoặc map về value
    @Column(name = "Role", length = 20)
    private Role role;

    @Column(name = "CreatedAt", insertable = false, updatable = false)
    private java.time.LocalDateTime createdAt;

    @Column(name = "UpdatedAt", insertable = false, updatable = false)
    private java.time.LocalDateTime updatedAt;
}

