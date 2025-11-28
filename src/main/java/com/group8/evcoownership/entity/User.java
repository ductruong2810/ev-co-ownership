package com.group8.evcoownership.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.group8.evcoownership.enums.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "Users")
//them cai nay de tranh lap lai role -> users
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "UserId", nullable = false)
    private Long userId;

    @Size(max = 100)
    @NotNull
    @Column(name = "FullName", nullable = false, length = 100)
    private String fullName;

    @Size(max = 100)
    @NotNull
    @Column(name = "Email", nullable = false, length = 100)
    private String email;

    @Size(max = 255)
    @NotNull
    @Column(name = "PasswordHash", nullable = false, length = 255)
    private String passwordHash;

    @Size(max = 20)
    @Column(name = "PhoneNumber", length = 20)
    private String phoneNumber;

    @Size(max = 500)
    @Column(name = "AvatarUrl", length = 500)
    private String avatarUrl;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "RoleId")
    private Role role;

    @Column(name = "Status", length = 20)
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

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