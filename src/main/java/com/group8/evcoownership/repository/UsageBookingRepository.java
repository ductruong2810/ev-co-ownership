package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.UsageBooking;
import com.group8.evcoownership.enums.BookingStatus;
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
                  AND Status IN ('Pending', 'Confirmed')
                  AND DATEPART(ISO_WEEK, StartDateTime) = DATEPART(ISO_WEEK, :weekStart)
                  AND YEAR(StartDateTime) = YEAR(:weekStart)
            """, nativeQuery = true)
    Long getTotalBookedHoursThisWeek(@Param("userId") Long userId,
                                     @Param("vehicleId") Long vehicleId,
                                     @Param("weekStart") LocalDateTime weekStart);

    // Kiểm tra trùng giờ với buffer 1h sau mỗi booking
    @Query(value = """
                SELECT COUNT(*)
                FROM UsageBooking
                WHERE VehicleId = :vehicleId
                  AND Status IN ('Pending', 'Confirmed')
                  AND (
                      (:start BETWEEN StartDateTime AND DATEADD(HOUR, 1, EndDateTime))
                      OR (:end BETWEEN StartDateTime AND DATEADD(HOUR, 1, EndDateTime))
                      OR (StartDateTime BETWEEN :start AND :end)
                  )
            """, nativeQuery = true)
    long countOverlappingBookingsWithBuffer(@Param("vehicleId") Long vehicleId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);


    // Lấy danh sách booking của xe trong 1 ngày (để hiển thị slot trống)
    @Query(value = """
                SELECT *
                FROM UsageBooking
                WHERE VehicleId = :vehicleId
                  AND Status IN ('Pending','Confirmed')
                  AND CAST(StartDateTime AS date) = :date
                ORDER BY StartDateTime
            """, nativeQuery = true)
    List<UsageBooking> findByVehicleIdAndDate(@Param("vehicleId") Long vehicleId,
                                              @Param("date") LocalDate date);


    // Lấy booking sắp tới của user (để hiển thị trong dashboard)
    @Query("""
                SELECT ub
                FROM UsageBooking ub
                WHERE ub.user.userId = :userId
                  AND ub.status IN ('Pending', 'Confirmed')
                  AND ub.startDateTime > CURRENT_TIMESTAMP
                ORDER BY ub.startDateTime ASC
            """)
    List<UsageBooking> findUpcomingBookingsByUser(@Param("userId") Long userId);

    // Lấy booking gần nhất đã hoàn thành (để tính thời gian buffer cho xe)
    List<UsageBooking> findTop1ByVehicleIdAndStatusOrderByEndDateTimeDesc(Long vehicle_id, BookingStatus status);
}

