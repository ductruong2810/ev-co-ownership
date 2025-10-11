package com.group8.evcoownership.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Entity
@Table(name = "PreUseCheck")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreUseCheck {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PreUseCheckId", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BookingId")
    private UsageBooking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserId")
    private User user;

    @Column(name = "ExteriorDamage")
    private Boolean exteriorDamage; // Có hư hỏng bên ngoài không?

    @Column(name = "InteriorClean")
    private Boolean interiorClean; // Nội thất có sạch không?

    @Column(name = "WarningLights")
    private Boolean warningLights; // Có đèn cảnh báo nào không?

    @Column(name = "TireCondition")
    private Boolean tireCondition; // Lốp có bình thường không?

    @Nationalized
    @Lob
    @Column(name = "UserNotes")
    private String userNotes; // Ghi chú của user

    @Column(name = "CheckTime")
    private LocalDateTime checkTime;

    @PrePersist
    public void onCreate() {
        checkTime = LocalDateTime.now();
    }
}
