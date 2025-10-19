package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    Optional<Vehicle> findByOwnershipGroup(OwnershipGroup ownershipGroup);
}
