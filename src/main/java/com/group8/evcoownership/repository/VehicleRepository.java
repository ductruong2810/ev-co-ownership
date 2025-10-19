package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    boolean existsByLicensePlateIgnoreCase(String licensePlate);
    boolean existsByChassisNumberIgnoreCase(String chassisNumber);
    Page<Vehicle> findByOwnershipGroupGroupId(Long groupId, Pageable pageable);
    Optional<Vehicle> findByOwnershipGroup(OwnershipGroup ownershipGroup);
}
