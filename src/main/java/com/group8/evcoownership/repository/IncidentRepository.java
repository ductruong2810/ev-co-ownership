package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {
}
