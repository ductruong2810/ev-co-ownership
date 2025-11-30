package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Dispute;
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
    @Query(
            value = """
                    SELECT * FROM "Dispute" d
                    WHERE (:status IS NULL OR d."Status" = :status)
                      AND (:disputeType IS NULL OR d."DisputeType" = :disputeType)
                      AND (:groupId IS NULL OR d."GroupId" = :groupId)
                      AND (:from IS NULL OR d."CreatedAt" >= CAST(:from AS timestamp))
                      AND (:to IS NULL OR d."CreatedAt" <= CAST(:to AS timestamp))
                    ORDER BY
                      CASE
                        WHEN d."Status" = 'OPEN' THEN 1
                        WHEN d."Status" = 'RESOLVED' THEN 2
                        WHEN d."Status" = 'REJECTED' THEN 3
                        ELSE 4
                      END,
                      d."CreatedAt" DESC
                    """,
            countQuery = """
                    SELECT COUNT(*) FROM "Dispute" d
                    WHERE (:status IS NULL OR d."Status" = :status)
                      AND (:disputeType IS NULL OR d."DisputeType" = :disputeType)
                      AND (:groupId IS NULL OR d."GroupId" = :groupId)
                      AND (:from IS NULL OR d."CreatedAt" >= CAST(:from AS timestamp))
                      AND (:to IS NULL OR d."CreatedAt" <= CAST(:to AS timestamp))
                    """,
            nativeQuery = true
    )
    Page<Dispute> findByFiltersOrdered(
            @Param("status") String status,
            @Param("disputeType") String disputeType,
            @Param("groupId") Long groupId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}


