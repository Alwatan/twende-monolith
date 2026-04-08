package tz.co.twende.ride.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.ride.entity.RideStatusEvent;

@Repository
public interface RideStatusEventRepository extends JpaRepository<RideStatusEvent, UUID> {

    List<RideStatusEvent> findByRideIdOrderByOccurredAtDesc(UUID rideId);
}
