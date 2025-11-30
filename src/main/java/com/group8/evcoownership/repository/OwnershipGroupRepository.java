package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.OwnershipGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

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
                    SELECT * FROM "OwnershipGroup" g
                    WHERE (:keyword IS NULL OR LOWER(g."GroupName") LIKE LOWER(CONCAT('%', :keyword, '%')))
                      AND (:status IS NULL OR g."Status" = :status)
                      AND (g."CreatedAt" >= CAST(:start AS timestamp) AND g."CreatedAt" <= CAST(:end AS timestamp))
                    ORDER BY
                      CASE
                        WHEN g."Status" = 'PENDING' THEN 0
                        WHEN g."Status" = 'ACTIVE' THEN 1
                        WHEN g."Status" = 'CLOSED' THEN 2
                        ELSE 3
                      END ,
                      g."CreatedAt" DESC
                    """,
            countQuery = """
                    SELECT COUNT(*) FROM "OwnershipGroup" g
                    WHERE (:keyword IS NULL OR LOWER(g."GroupName") LIKE LOWER(CONCAT('%', :keyword, '%')))
                      AND (:status IS NULL OR g."Status" = :status)
                      AND (g."CreatedAt" >= CAST(:start AS timestamp) AND g."CreatedAt" <= CAST(:end AS timestamp))
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

    @Query("""
            SELECT DISTINCT og
            FROM OwnershipGroup og
            JOIN OwnershipShare os ON og = os.group
            WHERE os.user.userId = :userId
            """)
    List<OwnershipGroup> findByMembersUserId(@Param("userId") Long userId);


}
