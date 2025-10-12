package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.VehicleCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleCheckRepository extends JpaRepository<VehicleCheck, Long> {

    @Query("SELECT vc FROM VehicleCheck vc WHERE vc.booking.vehicle.id = :vehicleId")
    List<VehicleCheck> findByVehicleId(@Param("vehicleId") Long vehicleId);

    List<VehicleCheck> findByBookingId(Long bookingId);

    @Query("SELECT vc FROM VehicleCheck vc WHERE vc.booking.vehicle.id = :vehicleId AND vc.checkType = :checkType ORDER BY vc.createdAt DESC")
    List<VehicleCheck> findByVehicleIdAndCheckTypeOrderByCreatedAtDesc(@Param("vehicleId") Long vehicleId, @Param("checkType") String checkType);

    @Query("SELECT vc FROM VehicleCheck vc WHERE vc.booking.vehicle.id = :vehicleId AND vc.checkType = :checkType ORDER BY vc.createdAt DESC")
    Optional<VehicleCheck> findTop1ByVehicleIdAndCheckTypeOrderByCreatedAtDesc(@Param("vehicleId") Long vehicleId, @Param("checkType") String checkType);

    List<VehicleCheck> findByStatus(String status);
}
