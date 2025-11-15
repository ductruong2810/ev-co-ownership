package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.VehicleImage;
import com.group8.evcoownership.enums.ImageApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleImageRepository extends JpaRepository<VehicleImage, Long> {

    List<VehicleImage> findByVehicleId(Long vehicleId);

    List<VehicleImage> findByApprovalStatus(ImageApprovalStatus status);

    Page<VehicleImage> findByApprovalStatus(ImageApprovalStatus status, Pageable pageable);

    List<VehicleImage> findByVehicleIdAndApprovalStatus(Long vehicleId, ImageApprovalStatus status);

    @Query("SELECT COUNT(vi) FROM VehicleImage vi WHERE vi.vehicle.Id = :vehicleId")
    long countByVehicleId(@Param("vehicleId") Long vehicleId);

    @Query("SELECT COUNT(vi) FROM VehicleImage vi WHERE vi.vehicle.Id = :vehicleId AND vi.approvalStatus = :status")
    long countByVehicleIdAndApprovalStatus(@Param("vehicleId") Long vehicleId, @Param("status") ImageApprovalStatus status);

    @Query("""
            SELECT DISTINCT vi
            FROM VehicleImage vi
            LEFT JOIN FETCH vi.approvedBy
            LEFT JOIN FETCH vi.vehicle v
            LEFT JOIN FETCH v.ownershipGroup g
            WHERE v.Id = :vehicleId
            """)
    List<VehicleImage> findDetailedByVehicleId(@Param("vehicleId") Long vehicleId);

    @Query("""
            SELECT DISTINCT vi
            FROM VehicleImage vi
            LEFT JOIN FETCH vi.approvedBy
            LEFT JOIN FETCH vi.vehicle v
            LEFT JOIN FETCH v.ownershipGroup g
            WHERE g.groupId = :groupId
            """)
    List<VehicleImage> findDetailedByGroupId(@Param("groupId") Long groupId);

    @Query("SELECT DISTINCT v.ownershipGroup FROM VehicleImage vi JOIN vi.vehicle v WHERE vi.approvalStatus = 'PENDING'")
    List<OwnershipGroup> findGroupsWithPendingImages();
}
