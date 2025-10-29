package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.OwnershipGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface OwnershipGroupRepository extends JpaRepository<OwnershipGroup, Long> {
    /**
     * Lọc và sắp xếp OwnershipGroup theo keyword, status, thời gian tạo
     * - keyword: tìm trong GroupName (ignore case)
     * - status: nếu null thì bỏ qua
     * - fromDate/toDate: lọc theo CreatedAt
     * - sort: PENDING → ACTIVE → CLOSED → khác, sau đó theo CreatedAt DESC
     */
    @Query(
            value = """
                    SELECT * FROM dbo.OwnershipGroup g
                    WHERE (:keyword IS NULL OR LOWER(g.GroupName) LIKE LOWER(CONCAT('%', :keyword, '%')))
                      AND (:status IS NULL OR g.Status = :status)
                      AND (g.CreatedAt BETWEEN :start AND :end)
                    ORDER BY
                      CASE
                        WHEN g.Status = 'PENDING' THEN 0
                        WHEN g.Status = 'ACTIVE' THEN 1
                        WHEN g.Status = 'CLOSED' THEN 2
                        ELSE 3
                      END ,
                      g.CreatedAt DESC
                    """,
            countQuery = """
                    SELECT COUNT(*) FROM dbo.OwnershipGroup g
                    WHERE (:keyword IS NULL OR LOWER(g.GroupName) LIKE LOWER(CONCAT('%', :keyword, '%')))
                      AND (:status IS NULL OR g.Status = :status)
                      AND (g.CreatedAt BETWEEN :start AND :end)
                    """,
            nativeQuery = true
    )
    Page<OwnershipGroup> findSortedGroups(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );

    // đã có
    boolean existsByGroupNameIgnoreCase(String groupName);

}
