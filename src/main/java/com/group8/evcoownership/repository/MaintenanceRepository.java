package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Maintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {
    List<Maintenance> findByVehicle_OwnershipGroup_GroupId(Long groupId);
}



