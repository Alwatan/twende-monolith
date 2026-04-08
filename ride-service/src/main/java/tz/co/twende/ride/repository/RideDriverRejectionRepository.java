package tz.co.twende.ride.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.ride.entity.RideDriverRejection;

@Repository
public interface RideDriverRejectionRepository extends JpaRepository<RideDriverRejection, UUID> {

    boolean existsByRideIdAndDriverId(UUID rideId, UUID driverId);

    List<RideDriverRejection> findByRideId(UUID rideId);

    int countByRideId(UUID rideId);
}
