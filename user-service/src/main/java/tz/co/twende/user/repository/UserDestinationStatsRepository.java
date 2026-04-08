package tz.co.twende.user.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.user.entity.UserDestinationStats;

@Repository
public interface UserDestinationStatsRepository extends JpaRepository<UserDestinationStats, UUID> {

    List<UserDestinationStats> findByUserIdAndCityIdOrderByTripCountDesc(
            UUID userId, UUID cityId, Pageable pageable);

    Optional<UserDestinationStats> findByUserIdAndCityIdAndDestinationLatAndDestinationLng(
            UUID userId, UUID cityId, BigDecimal destinationLat, BigDecimal destinationLng);
}
