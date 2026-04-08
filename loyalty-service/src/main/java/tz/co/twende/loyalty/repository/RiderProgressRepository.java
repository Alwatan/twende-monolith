package tz.co.twende.loyalty.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.loyalty.entity.RiderProgress;

@Repository
public interface RiderProgressRepository extends JpaRepository<RiderProgress, UUID> {

    Optional<RiderProgress> findByRiderIdAndCountryCodeAndVehicleType(
            UUID riderId, String countryCode, String vehicleType);

    List<RiderProgress> findByRiderId(UUID riderId);
}
