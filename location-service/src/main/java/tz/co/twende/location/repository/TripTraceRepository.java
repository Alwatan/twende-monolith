package tz.co.twende.location.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.twende.location.entity.TripTrace;

public interface TripTraceRepository extends JpaRepository<TripTrace, UUID> {

    Optional<TripTrace> findByRideId(UUID rideId);
}
