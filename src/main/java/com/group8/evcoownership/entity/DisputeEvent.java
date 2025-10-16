package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "DisputeEvent")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EventId", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TicketId")
    private DisputeTicket ticket;

    @Column(name = "ActorUserId")
    private Long actorUserId;

    @Column(name = "ActorRole", length = 20)
    private String actorRole; // USER/STAFF/SYSTEM

    @Column(name = "EventType", length = 40, nullable = false)
    private String eventType; // CREATED/ATTACHMENT_ADDED/.../CLOSED

    @Column(name = "OldValue", length = 200)
    private String oldValue;

    @Column(name = "NewValue", length = 200)
    private String newValue;

    @Lob
    @Column(name = "Note")
    private String note;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;
}


