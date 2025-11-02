package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.RejectionCategory;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Incident")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IncidentId", nullable = false)
    private Long id;

    // FK -> UsageBooking
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "BookingId", nullable = false)
    private UsageBooking booking;

    // FK -> Users (người gặp sự cố)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserId", nullable = false)
    private User user;

    @Nationalized
    @Lob
    @Column(name = "Description")
    private String description;

    @Column(name = "ActualCost", precision = 12, scale = 2)
    private BigDecimal actualCost;

    @Nationalized
    @Lob
    @Column(name = "ImageUrls")
    private String imageUrls; // có thể chứa danh sách ảnh dạng JSON hoặc chuỗi phân cách bằng dấu ;

    @Column(name = "Status", length = 20, nullable = false)
    private String status; // PENDING | APPROVED | REJECTED

    // FK -> Users (staff duyệt)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ApprovedBy")
    private User approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "RejectionCategory", length = 50)
    private RejectionCategory rejectionCategory; // loại lý do từ chối (enum chuẩn)

    @Nationalized
    @Lob
    @Column(name = "RejectionReason")
    private String rejectionReason; // ghi chú chi tiết do staff nhập


    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
