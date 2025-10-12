package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Entity
@Table(name = "Voting")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Voting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VotingId", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GroupId")
    private OwnershipGroup group;

    @Size(max = 255)
    @Nationalized
    @Column(name = "Title")
    private String title;

    @Nationalized
    @Lob
    @Column(name = "Description")
    private String description;

    @Column(name = "VotingType", length = 50)
    private String votingType; // BATTERY_UPGRADE, INSURANCE_CHANGE, SELL_VEHICLE, MAINTENANCE

    @Nationalized
    @Lob
    @Column(name = "Options")
    private String options; // JSON: ["Option 1", "Option 2", "Option 3"]

    @Nationalized
    @Lob
    @Column(name = "Results")
    private String results; // JSON: {"Option 1": 2, "Option 2": 1}

    @Column(name = "Deadline")
    private LocalDateTime deadline;

    @Column(name = "Status", length = 20)
    private String status; // ACTIVE, COMPLETED, CANCELLED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CreatedBy")
    private User createdBy;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
