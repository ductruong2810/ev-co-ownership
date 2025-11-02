package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}

