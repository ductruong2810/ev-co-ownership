package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.DocumentSide;
import com.group8.evcoownership.enums.DocumentStatus;
import com.group8.evcoownership.enums.DocumentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserId", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "DocumentType", length = 20)
    private DocumentType documentType; // CitizenID, DriverLicense

    @Enumerated(EnumType.STRING)
    @Column(name = "Side", length = 10)
    private DocumentSide side; // Front, Back

    @Column(name = "ImageUrl", length = 500, nullable = false)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", length = 20)
    private DocumentStatus status; // Pending, Approved, Rejected

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
