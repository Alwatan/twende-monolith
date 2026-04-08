package tz.co.twende.location.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tz.co.twende.location.entity.Zone;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    @Query(
            value =
                    """
            SELECT z.id, z.country_code, z.city_id, z.name,
                   ST_AsText(z.boundary) as boundary, z.type, z.config,
                   z.is_active, z.created_at, z.updated_at
            FROM zones z
            WHERE z.city_id = :cityId AND z.type = :type AND z.is_active = true
            AND ST_Covers(z.boundary, ST_Point(:lng, :lat)::geography)
            LIMIT 1
            """,
            nativeQuery = true)
    Optional<Zone> findActiveZoneContainingPoint(
            @Param("cityId") UUID cityId,
            @Param("type") String type,
            @Param("lng") BigDecimal lng,
            @Param("lat") BigDecimal lat);

    @Query(
            value =
                    """
            SELECT z.id, z.country_code, z.city_id, z.name,
                   ST_AsText(z.boundary) as boundary, z.type, z.config,
                   z.is_active, z.created_at, z.updated_at
            FROM zones z
            WHERE z.city_id = :cityId AND z.is_active = true
            AND ST_Covers(z.boundary, ST_Point(:lng, :lat)::geography)
            """,
            nativeQuery = true)
    List<Zone> findAllActiveZonesContainingPoint(
            @Param("cityId") UUID cityId,
            @Param("lng") BigDecimal lng,
            @Param("lat") BigDecimal lat);

    List<Zone> findByCityIdAndActiveTrue(UUID cityId);

    List<Zone> findByCityId(UUID cityId);
}
