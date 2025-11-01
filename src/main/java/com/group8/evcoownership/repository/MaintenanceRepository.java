package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Maintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {
    Optional<Maintenance> findFirstByVehicle_IdAndVehicle_OwnershipGroup_GroupIdAndStatusOrderByApprovalDateDescRequestDateDescCreatedAtDesc(
            Long vehicleId,
            Long groupId,
            String status
    );

    default Optional<Maintenance> findLatestApprovedMaintenance(Long vehicleId, Long groupId) {
        return findFirstByVehicle_IdAndVehicle_OwnershipGroup_GroupIdAndStatusOrderByApprovalDateDescRequestDateDescCreatedAtDesc(
                vehicleId,
                groupId,
                "APPROVED"
        );
    }

    // Kiểm tra có maintenance đang PENDING không (theo vehicleId và groupId)
    @Query("SELECT COUNT(m) > 0 FROM Maintenance m JOIN m.vehicle v WHERE v.Id = :vehicleId AND v.ownershipGroup.groupId = :groupId AND m.status = 'PENDING'")
    boolean existsByVehicle_IdAndGroupIdAndStatusPending(@Param("vehicleId") Long vehicleId, @Param("groupId") Long groupId);
    
    // Kiểm tra có maintenance đang được thực hiện không (APPROVED với ApprovalDate là hôm nay hoặc gần đây)
    @Query("""
                SELECT COUNT(m) > 0
                FROM Maintenance m
                JOIN m.vehicle v
                WHERE v.Id = :vehicleId
                  AND v.ownershipGroup.groupId = :groupId
                  AND m.status = 'APPROVED'
                  AND CAST(m.approvalDate AS date) = CURRENT_DATE
            """)
    boolean existsActiveMaintenance(@Param("vehicleId") Long vehicleId, @Param("groupId") Long groupId);
}



