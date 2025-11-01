package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {
    // Kiểm tra có incident chưa được resolve (status PENDING hoặc APPROVED) cho vehicle và group
    @Query("SELECT COUNT(i) > 0 FROM Incident i JOIN i.booking b JOIN b.vehicle v WHERE v.Id = :vehicleId AND v.ownershipGroup.groupId = :groupId AND i.status IN ('PENDING', 'APPROVED')")
    boolean existsUnresolvedIncidentsByVehicleIdAndGroupId(@Param("vehicleId") Long vehicleId, @Param("groupId") Long groupId);

}
