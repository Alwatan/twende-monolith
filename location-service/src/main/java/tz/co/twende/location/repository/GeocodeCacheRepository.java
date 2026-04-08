package tz.co.twende.location.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tz.co.twende.location.entity.GeocodeCache;

public interface GeocodeCacheRepository extends JpaRepository<GeocodeCache, UUID> {

    Optional<GeocodeCache> findByQueryHash(String queryHash);

    @Modifying
    @Query("DELETE FROM GeocodeCache g WHERE g.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
