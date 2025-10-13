package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.VehicleImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleImageRepository extends JpaRepository<VehicleImage, Long> {

    List<VehicleImage> findByVehicleId(Long vehicleId);

    List<VehicleImage> findByVehicleIdAndImageType(Long vehicleId, String imageType);

    void deleteByVehicleId(Long vehicleId);

    @Query("SELECT vi FROM VehicleImage vi WHERE vi.vehicle.id = :vehicleId ORDER BY vi.uploadedAt DESC")
    List<VehicleImage> findByVehicleIdOrderByUploadedAtDesc(@Param("vehicleId") Long vehicleId);
}
