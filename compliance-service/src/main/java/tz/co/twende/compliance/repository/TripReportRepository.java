package tz.co.twende.compliance.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tz.co.twende.compliance.entity.TripReport;

@Repository
public interface TripReportRepository extends JpaRepository<TripReport, UUID> {

    boolean existsByRideId(UUID rideId);

    List<TripReport> findByCountryCodeAndSubmittedFalseOrderByCreatedAtAsc(
            String countryCode, Pageable pageable);

    @Query(
            value =
                    "SELECT * FROM trip_reports t WHERE "
                            + "(CAST(:countryCode AS TEXT) IS NULL OR t.country_code = :countryCode) "
                            + "AND (CAST(:submitted AS BOOLEAN) IS NULL OR t.submitted = :submitted) "
                            + "AND (CAST(:fromTs AS TIMESTAMPTZ) IS NULL OR t.created_at >= :fromTs) "
                            + "AND (CAST(:toTs AS TIMESTAMPTZ) IS NULL OR t.created_at <= :toTs) "
                            + "ORDER BY t.created_at DESC",
            countQuery =
                    "SELECT COUNT(*) FROM trip_reports t WHERE "
                            + "(CAST(:countryCode AS TEXT) IS NULL OR t.country_code = :countryCode) "
                            + "AND (CAST(:submitted AS BOOLEAN) IS NULL OR t.submitted = :submitted) "
                            + "AND (CAST(:fromTs AS TIMESTAMPTZ) IS NULL OR t.created_at >= :fromTs) "
                            + "AND (CAST(:toTs AS TIMESTAMPTZ) IS NULL OR t.created_at <= :toTs)",
            nativeQuery = true)
    Page<TripReport> findWithFilters(
            @Param("countryCode") String countryCode,
            @Param("submitted") Boolean submitted,
            @Param("fromTs") Instant from,
            @Param("toTs") Instant to,
            Pageable pageable);

    long countByCountryCode(String countryCode);

    long countByCountryCodeAndSubmittedTrue(String countryCode);

    long countByCountryCodeAndSubmittedFalse(String countryCode);

    long countByCountryCodeAndSubmissionErrorIsNotNull(String countryCode);

    @Query("SELECT DISTINCT t.countryCode FROM TripReport t")
    List<String> findDistinctCountryCodes();

    @Query("SELECT MAX(t.submittedAt) FROM TripReport t WHERE t.countryCode = :countryCode")
    Instant findLastSubmissionAt(@Param("countryCode") String countryCode);
}
