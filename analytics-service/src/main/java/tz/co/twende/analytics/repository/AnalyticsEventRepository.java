package tz.co.twende.analytics.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tz.co.twende.analytics.entity.AnalyticsEvent;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, UUID> {

    List<AnalyticsEvent> findByEventTypeAndOccurredAtBetween(
            String eventType, Instant from, Instant to);

    List<AnalyticsEvent> findByActorIdAndOccurredAtBetween(UUID actorId, Instant from, Instant to);

    long countByEventTypeAndOccurredAtBetween(String eventType, Instant from, Instant to);

    long countByEventTypeAndCountryCodeAndOccurredAtBetween(
            String eventType, String countryCode, Instant from, Instant to);

    @Query(
            "SELECT COUNT(DISTINCT e.actorId) FROM AnalyticsEvent e "
                    + "WHERE e.eventType = :eventType AND e.occurredAt BETWEEN :from AND :to")
    long countDistinctActorByEventType(
            @Param("eventType") String eventType,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(
            "SELECT COUNT(DISTINCT e.actorId) FROM AnalyticsEvent e "
                    + "WHERE e.eventType = :eventType "
                    + "AND e.countryCode = :countryCode "
                    + "AND e.occurredAt BETWEEN :from AND :to")
    long countDistinctActorByEventTypeAndCountryCode(
            @Param("eventType") String eventType,
            @Param("countryCode") String countryCode,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(
            value =
                    "SELECT COALESCE(SUM(CAST(payload->>'amount' AS NUMERIC)), 0) "
                            + "FROM analytics_events "
                            + "WHERE event_type = :eventType "
                            + "AND occurred_at BETWEEN :from AND :to",
            nativeQuery = true)
    java.math.BigDecimal sumPayloadAmountByEventType(
            @Param("eventType") String eventType,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(
            value =
                    "SELECT COALESCE(SUM(CAST(payload->>'finalFare' AS NUMERIC)), 0) "
                            + "FROM analytics_events "
                            + "WHERE event_type = :eventType "
                            + "AND country_code = :countryCode "
                            + "AND occurred_at BETWEEN :from AND :to",
            nativeQuery = true)
    java.math.BigDecimal sumPayloadFinalFareByCountry(
            @Param("eventType") String eventType,
            @Param("countryCode") String countryCode,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(
            value =
                    "SELECT COALESCE(AVG(CAST(payload->>'finalFare' AS NUMERIC)), 0) "
                            + "FROM analytics_events "
                            + "WHERE event_type = :eventType "
                            + "AND country_code = :countryCode "
                            + "AND occurred_at BETWEEN :from AND :to",
            nativeQuery = true)
    java.math.BigDecimal avgPayloadFinalFareByCountry(
            @Param("eventType") String eventType,
            @Param("countryCode") String countryCode,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(
            value =
                    "SELECT payload->>'vehicleType' AS vehicleType, COUNT(*) AS cnt "
                            + "FROM analytics_events "
                            + "WHERE event_type = :eventType "
                            + "AND country_code = :countryCode "
                            + "AND occurred_at BETWEEN :from AND :to "
                            + "GROUP BY payload->>'vehicleType' "
                            + "ORDER BY cnt DESC "
                            + "LIMIT 1",
            nativeQuery = true)
    List<Object[]> findTopVehicleTypeByCountry(
            @Param("eventType") String eventType,
            @Param("countryCode") String countryCode,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
