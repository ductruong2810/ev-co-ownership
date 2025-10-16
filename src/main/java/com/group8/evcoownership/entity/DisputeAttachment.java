package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "DisputeAttachment")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AttachmentId", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DisputeId", nullable = false)
    private Dispute dispute;

    @Column(name = "FileName", length = 255, nullable = false)
    private String fileName;

    @Column(name = "MimeType", length = 100, nullable = false)
    private String mimeType;

    @Column(name = "SizeBytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "StorageUrl", length = 1000, nullable = false)
    private String storageUrl;

    @Column(name = "Sha256", length = 64)
    private String sha256;

    @Column(name = "ThumbnailUrl", length = 1000)
    private String thumbnailUrl;

    @Lob
    @Column(name = "MetaJson")
    private String metaJson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UploadedBy", nullable = false)
    private User uploadedBy;

    @Column(name = "Visibility", length = 20, nullable = false)
    private String visibility; // USER/STAFF/INTERNAL

    @Column(name = "Status", length = 20, nullable = false)
    private String status; // ACTIVE/SOFT_DELETED

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "DeletedAt")
    private LocalDateTime deletedAt;
}


