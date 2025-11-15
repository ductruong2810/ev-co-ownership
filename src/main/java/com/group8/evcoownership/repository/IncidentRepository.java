package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {
    List<Incident> findByReportedBy_UserId(Long userId);

    // ✅ Tìm các incident chưa được xử lý (PENDING) cho 1 xe cụ thể trong nhóm cụ thể
    @Query("""
                SELECT COUNT(i) > 0 
                FROM Incident i
                WHERE i.booking.vehicle.Id = :vehicleId
                  AND i.booking.vehicle.ownershipGroup.groupId = :groupId
                  AND i.status = 'PENDING'
            """)
    boolean existsUnresolvedIncidentsByVehicleIdAndGroupId(Long vehicleId, Long groupId);

    @Query("""
                SELECT i FROM Incident i
                WHERE (:status IS NULL OR i.status = :status)
                  AND (:startDate IS NULL OR i.createdAt >= CAST(:startDate AS timestamp))
                  AND (:endDate IS NULL OR i.createdAt <= CAST(:endDate AS timestamp))
                ORDER BY 
                  CASE 
                    WHEN i.status = 'PENDING' THEN 1
                    WHEN i.status = 'APPROVED' THEN 2
                    WHEN i.status = 'REJECTED' THEN 3
                    ELSE 4
                  END,
                  i.createdAt DESC
            """)
    Page<Incident> findByFiltersOrdered(
            @Param("status") String status,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            Pageable pageable
    );
}

