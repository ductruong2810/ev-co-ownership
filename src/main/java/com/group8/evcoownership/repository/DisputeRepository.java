package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Dispute;
import com.group8.evcoownership.enums.DisputeStatus;
import com.group8.evcoownership.enums.DisputeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    
    // Tìm tranh chấp theo người tạo
    List<Dispute> findByCreatedBy_UserId(Long userId);
    
    // Tìm tranh chấp theo nhóm
    List<Dispute> findByGroup_GroupId(Long groupId);
    
    // Tìm tranh chấp theo trạng thái
    List<Dispute> findByStatus(DisputeStatus status);
    
    // Tìm tranh chấp chờ xử lý (OPEN)
    List<Dispute> findByStatusOrderByCreatedAtDesc(DisputeStatus status);
    
    // Tìm tranh chấp theo nhóm và trạng thái
    List<Dispute> findByGroup_GroupIdAndStatus(Long groupId, DisputeStatus status);
    
    // Lọc tranh chấp với nhiều điều kiện (cho Staff/Admin)
    @Query("""
            SELECT d FROM Dispute d
            WHERE (:status IS NULL OR d.status = :status)
              AND (:disputeType IS NULL OR d.disputeType = :disputeType)
              AND (:groupId IS NULL OR d.group.groupId = :groupId)
              AND (:from IS NULL OR d.createdAt >= :from)
              AND (:to IS NULL OR d.createdAt <= :to)
            ORDER BY
              CASE
                WHEN d.status = 'OPEN' THEN 1
                WHEN d.status = 'RESOLVED' THEN 2
                WHEN d.status = 'REJECTED' THEN 3
                ELSE 4
              END,
              d.createdAt DESC
            """)
    Page<Dispute> findByFiltersOrdered(
            @Param("status") DisputeStatus status,
            @Param("disputeType") DisputeType disputeType,
            @Param("groupId") Long groupId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}

