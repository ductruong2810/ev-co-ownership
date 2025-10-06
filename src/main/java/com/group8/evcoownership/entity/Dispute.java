package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "Dispute")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Dispute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DisputeID")
    private Long disputeId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "FundID", nullable = false)
    private SharedFund fund;

    @ManyToOne(optional = false)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @Lob
    @Column(name = "Description")
    private String description;

    @Column(name = "Status", length = 20)
    private String status; // Open|Resolved|Rejected

    @Lob
    @Column(name = "ResolutionNote")
    private String resolutionNote;

    @ManyToOne
    @JoinColumn(name = "ResolvedBy")
    private User resolvedBy;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;
}
