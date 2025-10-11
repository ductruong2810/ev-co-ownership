package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.VehicleRejection;
import com.group8.evcoownership.enums.RejectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleRejectionRepository extends JpaRepository<VehicleRejection, Long> {

    List<VehicleRejection> findByVehicleIdAndStatus(Long vehicleId, RejectionStatus status);

    List<VehicleRejection> findByStatus(RejectionStatus status);

    @Query("SELECT vr FROM VehicleRejection vr WHERE vr.vehicle.id = :vehicleId ORDER BY vr.rejectedAt DESC")
    List<VehicleRejection> findByVehicleIdOrderByRejectedAtDesc(@Param("vehicleId") Long vehicleId);
}
