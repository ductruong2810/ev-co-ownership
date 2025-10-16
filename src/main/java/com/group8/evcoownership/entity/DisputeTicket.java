package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "DisputeTicket")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeTicket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TicketId", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DisputeId", nullable = false)
    private Dispute dispute;

    @Column(name = "Priority", length = 20)
    private String priority; // LOW/MEDIUM/HIGH

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "AssignedTo")
    private User assignedTo;

    @Column(name = "OpenedAt")
    private LocalDateTime openedAt;

    @Column(name = "DueFirstResponseAt")
    private LocalDateTime dueFirstResponseAt;

    @Column(name = "DueResolutionAt")
    private LocalDateTime dueResolutionAt;

    @Column(name = "ClosedAt")
    private LocalDateTime closedAt;
}


