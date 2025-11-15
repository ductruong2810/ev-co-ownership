package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Maintenance;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.MaintenanceCoverageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {
    // ===== GET ALL (sort theo status -> createdAt DESC) =====
    @Query("""
                SELECT m FROM Maintenance m
                ORDER BY
                  CASE
                    WHEN m.status = 'PENDING' THEN 1
                    WHEN m.status = 'APPROVED' THEN 2
                    WHEN m.status = 'REJECTED' THEN 3
                    ELSE 4
                  END,
                  m.createdAt DESC
            """)
    List<Maintenance> findAllSorted();

    @Query("""
                SELECT m FROM Maintenance m
                WHERE m.requestedBy.email = :email
                ORDER BY
                  CASE 
                    WHEN m.status = 'PENDING' THEN 1
                    WHEN m.status = 'APPROVED' THEN 3
                    WHEN m.status = 'REJECTED' THEN 5
                    ELSE 4
                  END,
                  m.createdAt DESC
            """)
    List<Maintenance> findAllByTechnicianEmailSorted(@Param("email") String email);

    // ===================================================

    List<Maintenance> findByRequestedBy(User user);

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

    // Nếu muốn lấy tất cả maintenance mà user là người phải trả
    List<Maintenance> findByLiableUser_UserIdOrderByRequestDateDesc(Long userId);

    // Nếu muốn filter riêng cho PERSONAL
    List<Maintenance> findByCoverageTypeAndLiableUser_UserIdOrderByRequestDateDesc(
            MaintenanceCoverageType coverageType,
            Long userId
    );

    // Lấy tất cả maintenance theo coverage type, sort mới nhất trước
    List<Maintenance> findByCoverageTypeOrderByRequestDateDesc(MaintenanceCoverageType coverageType);

    List<Maintenance> findByCoverageTypeAndStatusOrderByRequestDateDesc(MaintenanceCoverageType coverageType, String status);

    List<Maintenance> findByCoverageTypeAndRequestedBy_UserIdOrderByRequestDateDesc(MaintenanceCoverageType coverageType, Long requestedByUserId);

}



