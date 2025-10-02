package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Booking;
import com.group8.evcoownership.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("SELECT b FROM Booking b " +
            "WHERE b.vehicle = :vehicle " +
            "AND b.status IN ('Pending','Confirmed','Buffer') " +
            "AND (b.startDateTime < :endDateTime AND b.endDateTime > :startDateTime)")
    List<Booking> findOverlaps(Vehicle vehicle,
                               LocalDateTime startDateTime,
                               LocalDateTime endDateTime);
}

