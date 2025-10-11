package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.VehicleReport;
import com.group8.evcoownership.enums.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleReportRepository extends JpaRepository<VehicleReport, Long> {

    @Query("SELECT vr FROM VehicleReport vr WHERE vr.booking.vehicle.id = :vehicleId")
    List<VehicleReport> findByVehicleId(@Param("vehicleId") Long vehicleId);

    List<VehicleReport> findByBookingId(Long bookingId);

    @Query("SELECT vr FROM VehicleReport vr WHERE vr.booking.vehicle.id = :vehicleId AND vr.reportType = :reportType ORDER BY vr.createdAt DESC")
    List<VehicleReport> findByVehicleIdAndReportTypeOrderByCreatedAtDesc(@Param("vehicleId") Long vehicleId, @Param("reportType") ReportType reportType);

    @Query("SELECT vr FROM VehicleReport vr WHERE vr.booking.vehicle.id = :vehicleId AND vr.reportType = :reportType ORDER BY vr.createdAt DESC")
    Optional<VehicleReport> findTop1ByVehicleIdAndReportTypeOrderByCreatedAtDesc(@Param("vehicleId") Long vehicleId, @Param("reportType") ReportType reportType);
}
