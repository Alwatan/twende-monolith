package tz.co.twende.compliance.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tz.co.twende.compliance.entity.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query(
            value =
                    "SELECT * FROM audit_log a WHERE "
                            + "(CAST(:countryCode AS TEXT) IS NULL OR a.country_code = :countryCode) "
                            + "AND (CAST(:eventType AS TEXT) IS NULL OR a.event_type = :eventType) "
                            + "AND (CAST(:entityId AS UUID) IS NULL OR a.entity_id = :entityId) "
                            + "AND (CAST(:fromTs AS TIMESTAMPTZ) IS NULL OR a.occurred_at >= :fromTs) "
                            + "AND (CAST(:toTs AS TIMESTAMPTZ) IS NULL OR a.occurred_at <= :toTs) "
                            + "ORDER BY a.occurred_at DESC",
            countQuery =
                    "SELECT COUNT(*) FROM audit_log a WHERE "
                            + "(CAST(:countryCode AS TEXT) IS NULL OR a.country_code = :countryCode) "
                            + "AND (CAST(:eventType AS TEXT) IS NULL OR a.event_type = :eventType) "
                            + "AND (CAST(:entityId AS UUID) IS NULL OR a.entity_id = :entityId) "
                            + "AND (CAST(:fromTs AS TIMESTAMPTZ) IS NULL OR a.occurred_at >= :fromTs) "
                            + "AND (CAST(:toTs AS TIMESTAMPTZ) IS NULL OR a.occurred_at <= :toTs)",
            nativeQuery = true)
    Page<AuditLog> findWithFilters(
            @Param("countryCode") String countryCode,
            @Param("eventType") String eventType,
            @Param("entityId") UUID entityId,
            @Param("fromTs") Instant from,
            @Param("toTs") Instant to,
            Pageable pageable);
}
