package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Maintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {
    // Order by ApprovalDate DESC (when maintenance was completed), fallback to RequestDate DESC
    @Query("SELECT m FROM Maintenance m JOIN m.vehicle v WHERE v.Id = :vehicleId AND v.ownershipGroup.groupId = :groupId AND m.status = 'APPROVED' ORDER BY m.approvalDate DESC, m.requestDate DESC, m.createdAt DESC")
    Optional<Maintenance> findTopByVehicle_IdAndGroupIdAndStatusApprovedOrderByCreatedAtDesc(@Param("vehicleId") Long vehicleId, @Param("groupId") Long groupId);

    // Kiểm tra có maintenance đang PENDING không (theo vehicleId và groupId)
    @Query("SELECT COUNT(m) > 0 FROM Maintenance m JOIN m.vehicle v WHERE v.Id = :vehicleId AND v.ownershipGroup.groupId = :groupId AND m.status = 'PENDING'")
    boolean existsByVehicle_IdAndGroupIdAndStatusPending(@Param("vehicleId") Long vehicleId, @Param("groupId") Long groupId);
}



