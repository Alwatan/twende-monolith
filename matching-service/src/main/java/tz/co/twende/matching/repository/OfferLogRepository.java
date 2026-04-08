package tz.co.twende.matching.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.matching.entity.OfferLog;

@Repository
public interface OfferLogRepository extends JpaRepository<OfferLog, UUID> {

    List<OfferLog> findByRideId(UUID rideId);

    Optional<OfferLog> findByRideIdAndDriverId(UUID rideId, UUID driverId);
}
