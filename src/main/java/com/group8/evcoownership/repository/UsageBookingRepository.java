package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.UsageBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface UsageBookingRepository extends JpaRepository<UsageBooking, Long> {

    //Tổng số giờ user đã đặt trong tuần (để kiểm tra quota)
    @Query(value = """
                SELECT COALESCE(SUM(DATEDIFF(HOUR, StartDateTime, EndDateTime)), 0)
                FROM UsageBooking
                WHERE UserId = :userId
                  AND VehicleId = :vehicleId
                  AND Status = 'CONFIRMED'
                  AND DATEPART(ISO_WEEK, StartDateTime) = DATEPART(ISO_WEEK, :weekStart)
                  AND YEAR(StartDateTime) = YEAR(:weekStart)
            """, nativeQuery = true)
    Long getTotalBookedHoursThisWeek(@Param("userId") Long userId,
                                     @Param("vehicleId") Long vehicleId,
                                     @Param("weekStart") LocalDateTime weekStart);

    //Tính quota limit dựa trên ownership percentage (168h/tuần * ownership%)
    @Query(value = """
                SELECT CAST(168 * (os.OwnershipPercentage / 100.0) AS INT)
                FROM OwnershipShare os
                INNER JOIN Vehicle v ON v.GroupId = os.GroupId
                WHERE os.UserId = :userId AND v.VehicleId = :vehicleId
            """, nativeQuery = true)
    Long getQuotaLimitByOwnershipPercentage(@Param("userId") Long userId,
                                            @Param("vehicleId") Long vehicleId);

    // Kiểm tra trùng giờ với buffer 1h (loại trừ booking hiện tại)
    @Query(value = """
                SELECT COUNT(*)
                FROM UsageBooking
                WHERE VehicleId = :vehicleId
                  AND BookingId != :excludeBookingId
                  AND Status = 'CONFIRMED'
                  AND (
                      (:start BETWEEN StartDateTime AND DATEADD(HOUR, 1, EndDateTime))
                      OR (:end BETWEEN StartDateTime AND DATEADD(HOUR, 1, EndDateTime))
                      OR (StartDateTime BETWEEN :start AND :end)
                  )
            """, nativeQuery = true)
    long countOverlappingBookingsWithBufferExcluding(@Param("vehicleId") Long vehicleId,
                                                     @Param("excludeBookingId") Long excludeBookingId,
                                                     @Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end);


    // Lấy danh sách booking của xe trong 1 ngày với thông tin user (để hiển thị slot trống)
    @Query("""
                SELECT ub
                FROM UsageBooking ub
                JOIN FETCH ub.user u
                WHERE ub.vehicle.Id = :vehicleId
                  AND ub.status = 'CONFIRMED'
                  AND CAST(ub.startDateTime AS date) = :date
                ORDER BY ub.startDateTime
            """)
    List<UsageBooking> findByVehicleIdAndDateWithUser(@Param("vehicleId") Long vehicleId,
                                                      @Param("date") LocalDate date);


    // Lấy booking sắp tới của user (để hiển thị trong dashboard)
    @Query("""
                SELECT ub
                FROM UsageBooking ub
                WHERE ub.user.userId = :userId
                  AND ub.status = 'CONFIRMED'
                  AND ub.startDateTime > CURRENT_TIMESTAMP
                ORDER BY ub.startDateTime ASC
            """)
    List<UsageBooking> findUpcomingBookingsByUser(@Param("userId") Long userId);


    @Query("""
            SELECT b FROM UsageBooking b
            WHERE b.user.userId = :userId
            AND b.startDateTime >= :weekStart
            AND b.startDateTime < :weekEnd
            ORDER BY b.startDateTime
            """)
    List<UsageBooking> findBookingsByUserInWeek(
            @Param("userId") Long userId,
            @Param("weekStart") LocalDateTime weekStart,
            @Param("weekEnd") LocalDateTime weekEnd
    );


    // Tìm các booking bị ảnh hưởng bởi maintenance period
    @Query("""
                SELECT ub
                FROM UsageBooking ub
                JOIN FETCH ub.user u
                WHERE ub.vehicle.Id = :vehicleId
                  AND ub.status = 'CONFIRMED'
                  AND (
                      (ub.startDateTime BETWEEN :startDateTime AND :endDateTime)
                      OR (ub.endDateTime BETWEEN :startDateTime AND :endDateTime)
                      OR (ub.startDateTime <= :startDateTime AND ub.endDateTime >= :endDateTime)
                  )
                ORDER BY ub.startDateTime
            """)
    List<UsageBooking> findAffectedBookings(@Param("vehicleId") Long vehicleId,
                                            @Param("startDateTime") LocalDateTime startDateTime,
                                            @Param("endDateTime") LocalDateTime endDateTime);

    // Find latest completed booking for a vehicle and group (to get vehicle status from POST_USE check)
    @Query("""
                SELECT ub
                FROM UsageBooking ub
                JOIN FETCH ub.vehicle v
                WHERE v.Id = :vehicleId
                  AND v.ownershipGroup.groupId = :groupId
                  AND ub.status IN ('CONFIRMED', 'COMPLETED')
                  AND ub.endDateTime <= CURRENT_TIMESTAMP
                ORDER BY ub.endDateTime DESC
            """)
    List<UsageBooking> findLatestCompletedBookingByVehicleAndGroup(@Param("vehicleId") Long vehicleId, @Param("groupId") Long groupId);

    // Find active booking of user for vehicle (for QR check-in)
    @Query("""
                SELECT ub
                FROM UsageBooking ub
                WHERE ub.user.userId = :userId
                  AND ub.vehicle.Id = :vehicleId
                  AND ub.status = 'CONFIRMED'
                  AND ub.startDateTime <= CURRENT_TIMESTAMP
                  AND ub.endDateTime >= CURRENT_TIMESTAMP
                ORDER BY ub.startDateTime DESC
            """)
    List<UsageBooking> findActiveBookingsByUserAndVehicle(@Param("userId") Long userId,
                                                          @Param("vehicleId") Long vehicleId);

}

