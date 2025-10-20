package com.group8.evcoownership.repository;

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

    List<VehicleImage> findByVehicleIdAndImageType(Long vehicleId, String imageType);

    List<VehicleImage> findByApprovalStatus(ImageApprovalStatus status);

    Page<VehicleImage> findByApprovalStatus(ImageApprovalStatus status, Pageable pageable);

    List<VehicleImage> findByVehicleIdAndApprovalStatus(Long vehicleId, ImageApprovalStatus status);

    @Query("SELECT COUNT(vi) FROM VehicleImage vi WHERE vi.vehicle.id = :vehicleId")
    long countByVehicleId(@Param("vehicleId") Long vehicleId);

    @Query("SELECT COUNT(vi) FROM VehicleImage vi WHERE vi.vehicle.id = :vehicleId AND vi.approvalStatus = :status")
    long countByVehicleIdAndApprovalStatus(@Param("vehicleId") Long vehicleId, @Param("status") ImageApprovalStatus status);

    List<VehicleImage> findByVehicle_OwnershipGroup_GroupId(Long groupId);

    @Query("SELECT DISTINCT v.ownershipGroup FROM VehicleImage vi JOIN vi.vehicle v WHERE vi.approvalStatus = 'PENDING'")
    List<com.group8.evcoownership.entity.OwnershipGroup> findGroupsWithPendingImages();

    void deleteByVehicleId(Long vehicleId);

    @Query("SELECT vi FROM VehicleImage vi WHERE vi.vehicle.id = :vehicleId ORDER BY vi.uploadedAt DESC")
    List<VehicleImage> findByVehicleIdOrderByUploadedAtDesc(@Param("vehicleId") Long vehicleId);
}
