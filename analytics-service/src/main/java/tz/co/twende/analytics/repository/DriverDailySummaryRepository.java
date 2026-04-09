package tz.co.twende.analytics.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tz.co.twende.analytics.entity.DriverDailySummary;

public interface DriverDailySummaryRepository extends JpaRepository<DriverDailySummary, UUID> {

    List<DriverDailySummary> findByDriverIdAndDateBetweenOrderByDateAsc(
            UUID driverId, LocalDate from, LocalDate to);

    @Query(
            "SELECT COALESCE(SUM(s.totalEarned), 0), "
                    + "COALESCE(SUM(s.tripCount), 0), "
                    + "COALESCE(SUM(s.onlineHours), 0) "
                    + "FROM DriverDailySummary s "
                    + "WHERE s.driverId = :driverId AND s.date BETWEEN :from AND :to")
    Object[] aggregateByDriverAndDateRange(
            @Param("driverId") UUID driverId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query(
            "SELECT SUM(s.tripCount) FROM DriverDailySummary s "
                    + "WHERE s.driverId = :driverId AND s.date BETWEEN :from AND :to")
    Integer sumTripCountByDriverAndDateRange(
            @Param("driverId") UUID driverId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query(
            "SELECT SUM(s.totalEarned) FROM DriverDailySummary s "
                    + "WHERE s.driverId = :driverId AND s.date BETWEEN :from AND :to")
    BigDecimal sumTotalEarnedByDriverAndDateRange(
            @Param("driverId") UUID driverId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
