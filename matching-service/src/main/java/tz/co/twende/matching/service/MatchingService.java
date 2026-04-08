package tz.co.twende.matching.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tz.co.twende.common.event.ride.RideRequestedEvent;
import tz.co.twende.matching.client.LocationServiceClient;
import tz.co.twende.matching.dto.DriverCandidate;
import tz.co.twende.matching.dto.NearbyDriverDto;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingService {

    private static final double INITIAL_RADIUS_KM = 3.0;
    private static final int MAX_CANDIDATES = 30;

    private final LocationServiceClient locationServiceClient;
    private final DriverScoringService driverScoringService;
    private final BroadcastService broadcastService;
    private final StringRedisTemplate stringRedisTemplate;

    public void onRideRequested(RideRequestedEvent event) {
        UUID rideId = event.getRideId();
        String countryCode = event.getCountryCode();
        String vehicleType = event.getVehicleType().name();
        BigDecimal pickupLat = event.getPickupLocation().getLatitude();
        BigDecimal pickupLng = event.getPickupLocation().getLongitude();

        log.info(
                "Processing ride request {} for vehicle type {} at ({}, {})",
                rideId,
                vehicleType,
                pickupLat,
                pickupLng);

        // Find nearby drivers
        List<NearbyDriverDto> nearbyDrivers =
                locationServiceClient.findNearbyDrivers(
                        countryCode, vehicleType, pickupLat, pickupLng, INITIAL_RADIUS_KM);

        if (nearbyDrivers.isEmpty()) {
            log.warn("No nearby drivers found for ride {}", rideId);
            return;
        }

        // Filter out previously offered and rejected drivers
        List<NearbyDriverDto> filtered = filterCandidates(rideId, nearbyDrivers);

        if (filtered.isEmpty()) {
            log.warn("All nearby drivers already offered/rejected for ride {}", rideId);
            return;
        }

        // Score candidates
        List<DriverCandidate> scored =
                driverScoringService.scoreAndRank(filtered, INITIAL_RADIUS_KM);

        // Take top candidates
        List<DriverCandidate> topCandidates = scored.stream().limit(MAX_CANDIDATES).toList();

        // Set initial batch counter
        stringRedisTemplate.opsForValue().set("ride_offer_batches:" + rideId, "1");

        // Broadcast offers
        String pickupAddress =
                event.getPickupLocation().getAddress() != null
                        ? event.getPickupLocation().getAddress()
                        : "Unknown location";

        broadcastService.broadcastOffers(
                rideId,
                event.getRiderId(),
                countryCode,
                topCandidates,
                1,
                event.getEstimatedFare(),
                BigDecimal.ZERO,
                "TZS",
                pickupAddress,
                BigDecimal.ZERO,
                0);

        log.info("Broadcast ride {} to {} candidates (batch 1)", rideId, topCandidates.size());
    }

    public List<NearbyDriverDto> filterCandidates(UUID rideId, List<NearbyDriverDto> candidates) {
        Set<String> alreadyOffered =
                stringRedisTemplate.opsForSet().members("rides_offered_to:" + rideId);
        Set<String> rejected = stringRedisTemplate.opsForSet().members("driver_rejected:" + rideId);

        return candidates.stream()
                .filter(
                        d -> {
                            String id = d.getDriverId().toString();
                            boolean offered = alreadyOffered != null && alreadyOffered.contains(id);
                            boolean rej = rejected != null && rejected.contains(id);
                            return !offered && !rej;
                        })
                .collect(Collectors.toList());
    }
}
