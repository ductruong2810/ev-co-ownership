package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.minidev.json.annotate.JsonIgnore;

import java.time.LocalDateTime;

@Entity
@Table(name = "UserDocument")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long documentId;

    @Column(name = "DocumentNumber", unique = true)
    private String documentNumber; // Số CCCD hoặc số GPLX

    @Column(name = "DateOfBirth", length = 20)
    private String dateOfBirth;

    @Column(name = "IssueDate", length = 20)
    private String issueDate;

    @Column(name = "ExpiryDate", length = 20)
    private String expiryDate;

    @Column(name = "Address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "UserId", nullable = false)
    private Long userId;

    @Column(name = "DocumentType", length = 20)
    private String documentType; // CITIZEN_ID, DRIVER_LICENSE

    @Column(name = "Side", length = 10)
    private String side; // FRONT, BACK

    @Column(name = "ImageUrl", length = 500, nullable = false)
    private String imageUrl;

    //them vao 23/10/2025
    @Column(name = "FileHash", length = 64)
    private String fileHash;

    @Column(name = "Status", length = 20)
    private String status; // PENDING, APPROVED, REJECTED

    @JsonIgnore
    @Column(name = "ReviewNote")
    private String reviewNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ReviewedBy")
    private User reviewedBy;

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
