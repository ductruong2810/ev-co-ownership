package com.group8.evcoownership.entity;

import com.group8.evcoownership.enums.RejectionCategory;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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

    // FK → UsageBooking (liên kết với chuyến xe khi xảy ra sự cố)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "BookingId", nullable = false)
    private UsageBooking booking;

    // FK → Users (người đồng sở hữu / co-owner báo cáo sự cố)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserId", nullable = false)
    private User reportedBy;


    // Mô tả chi tiết sự cố
    @Nationalized
    @Lob
    @Column(name = "Description")
    private String description;

    // Chi phí thực tế (nếu có)
    @Column(name = "ActualCost", precision = 12, scale = 2)
    private BigDecimal actualCost;

    // Danh sách URL ảnh minh họa, có thể phân cách bằng dấu ";"
    @Nationalized
    @Lob
    @Column(name = "ImageUrls")
    private String imageUrls;

    // Trạng thái xử lý
    @Column(name = "Status", length = 20, nullable = false)
    private String status; // PENDING | APPROVED | REJECTED

    // FK → Users (staff hoặc admin phê duyệt)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ApprovedBy")
    private User approvedBy;

    // Loại lý do từ chối (enum chuẩn hóa)
    @Enumerated(EnumType.STRING)
    @Column(name = "RejectionCategory", length = 50)
    private RejectionCategory rejectionCategory;

    // Ghi chú chi tiết (staff nhập tay)
    @Nationalized
    @Lob
    @Column(name = "RejectionReason")
    private String rejectionReason;

    // Thời điểm tạo
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Thời điểm cập nhật
    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;

    // Auto set timestamp & default status
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = "PENDING";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
