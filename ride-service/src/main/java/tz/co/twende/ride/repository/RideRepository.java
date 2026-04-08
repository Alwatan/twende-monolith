package tz.co.twende.ride.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.common.enums.RideStatus;
import tz.co.twende.ride.entity.Ride;

@Repository
public interface RideRepository extends JpaRepository<Ride, UUID> {

    Optional<Ride> findByIdAndRiderId(UUID id, UUID riderId);

    Optional<Ride> findByIdAndDriverId(UUID id, UUID driverId);

    List<Ride> findByRiderIdAndStatusIn(UUID riderId, List<RideStatus> statuses);

    List<Ride> findByDriverIdAndStatusIn(UUID driverId, List<RideStatus> statuses);

    Page<Ride> findByRiderIdOrderByCreatedAtDesc(UUID riderId, Pageable pageable);

    List<Ride> findByStatusAndMatchingTimeoutAtBefore(RideStatus status, Instant cutoff);

    Page<Ride> findByRiderIdAndCityIdAndStatusOrderByCompletedAtDesc(
            UUID riderId, UUID cityId, RideStatus status, Pageable pageable);
}
