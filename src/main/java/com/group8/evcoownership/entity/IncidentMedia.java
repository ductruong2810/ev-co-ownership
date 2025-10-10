package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.MediaType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Entity
@Table(name = "IncidentMedia")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentMedia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MediaId", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "IncidentId", nullable = false)
    private Incident incident;

    @Size(max = 500)
    @NotNull
    @Nationalized
    @Column(name = "MediaUrl", nullable = false, length = 500)
    private String mediaUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "MediaType", length = 20)
    private MediaType mediaType;

    @Column(name = "UploadedAt")
    private LocalDateTime uploadedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UploadedBy")
    private User uploadedBy;

}