package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.VehicleCheck;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleCheckRepository extends JpaRepository<VehicleCheck, Long> {

    @Query("SELECT vc FROM VehicleCheck vc WHERE vc.booking.vehicle.Id = :vehicleId")
    List<VehicleCheck> findByVehicleId(@Param("vehicleId") Long vehicleId);

    List<VehicleCheck> findByBookingId(Long bookingId);

    // Find latest POST_USE check from bookings of a specific group and vehicle
    @Query("""
                SELECT vc FROM VehicleCheck vc
                JOIN vc.booking b
                JOIN b.vehicle v
                WHERE v.Id = :vehicleId
                  AND v.ownershipGroup.groupId = :groupId
                  AND vc.checkType = 'POST_USE'
                ORDER BY vc.createdAt DESC, vc.id DESC
            """)
    List<VehicleCheck> findLatestPostUseCheckByVehicleAndGroup(@Param("vehicleId") Long vehicleId, @Param("groupId") Long groupId, Pageable pageable);

    // Check if there are POST_USE checks with issues or REJECTED status for vehicle and group
    @Query("""
                SELECT COUNT(vc) > 0 FROM VehicleCheck vc
                JOIN vc.booking b
                JOIN b.vehicle v
                WHERE v.Id = :vehicleId
                  AND v.ownershipGroup.groupId = :groupId
                  AND vc.checkType = 'POST_USE'
                  AND (vc.status = 'REJECTED' OR (vc.issues IS NOT NULL AND vc.issues != ''))
            """)
    boolean existsPostUseCheckWithIssuesByVehicleAndGroup(@Param("vehicleId") Long vehicleId, @Param("groupId") Long groupId);

    List<VehicleCheck> findByStatus(String status);
}
