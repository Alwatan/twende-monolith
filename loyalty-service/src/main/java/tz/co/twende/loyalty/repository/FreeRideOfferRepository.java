package tz.co.twende.loyalty.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tz.co.twende.loyalty.entity.FreeRideOffer;

@Repository
public interface FreeRideOfferRepository extends JpaRepository<FreeRideOffer, UUID> {

    Optional<FreeRideOffer>
            findFirstByRiderIdAndCountryCodeAndVehicleTypeAndStatusAndExpiresAtAfterOrderByEarnedAtAsc(
                    UUID riderId,
                    String countryCode,
                    String vehicleType,
                    String status,
                    Instant now);

    List<FreeRideOffer> findByRiderIdAndStatus(UUID riderId, String status);

    List<FreeRideOffer> findByStatusAndExpiresAtBefore(String status, Instant now);

    @Modifying
    @Query(
            "UPDATE FreeRideOffer o SET o.status = 'EXPIRED' WHERE o.status = 'AVAILABLE' AND"
                    + " o.expiresAt < :now")
    int expireOffersBefore(@Param("now") Instant now);
}
