package tz.co.twende.user.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.user.dto.DestinationSuggestionsDto;
import tz.co.twende.user.dto.FrequentDestinationDto;
import tz.co.twende.user.dto.RecentRideDto;
import tz.co.twende.user.entity.UserDestinationStats;
import tz.co.twende.user.repository.UserDestinationStatsRepository;
import tz.co.twende.user.repository.UserProfileRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class DestinationSuggestionService {

    private static final int MAX_FREQUENT = 4;
    private static final int MAX_RECENT = 5;
    private static final int COORDINATE_SCALE = 4;

    /** Hardcoded city mapping for Tanzania. Dar es Salaam bounding box. */
    private static final UUID DAR_ES_SALAAM_CITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final UserDestinationStatsRepository destinationStatsRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional
    public void onRideCompleted(RideCompletedEvent event) {
        if (event.getRiderId() == null) {
            log.warn("RideCompletedEvent missing riderId, skipping destination stats");
            return;
        }
        if (event.getDropoffLat() == null || event.getDropoffLng() == null) {
            log.warn(
                    "RideCompletedEvent for ride {} missing dropoff coordinates, skipping",
                    event.getRideId());
            return;
        }

        if (!userProfileRepository.existsById(event.getRiderId())) {
            log.debug(
                    "User profile not found for rider {}, skipping destination stats",
                    event.getRiderId());
            return;
        }

        BigDecimal lat = roundTo4Dp(event.getDropoffLat());
        BigDecimal lng = roundTo4Dp(event.getDropoffLng());
        UUID cityId = event.getCityId() != null ? event.getCityId() : resolveCityId(lat, lng);
        String countryCode = event.getCountryCode() != null ? event.getCountryCode() : "TZ";
        Instant completedAt =
                event.getCompletedAt() != null ? event.getCompletedAt() : Instant.now();

        Optional<UserDestinationStats> existing =
                destinationStatsRepository.findByUserIdAndCityIdAndDestinationLatAndDestinationLng(
                        event.getRiderId(), cityId, lat, lng);

        if (existing.isPresent()) {
            UserDestinationStats stats = existing.get();
            stats.setTripCount(stats.getTripCount() + 1);
            stats.setLastTripAt(completedAt);
            if (event.getDropoffAddress() != null) {
                stats.setDestinationAddress(event.getDropoffAddress());
            }
            destinationStatsRepository.save(stats);
            log.debug(
                    "Updated destination stats for rider {} at ({}, {}), count={}",
                    event.getRiderId(),
                    lat,
                    lng,
                    stats.getTripCount());
        } else {
            UserDestinationStats stats = new UserDestinationStats();
            stats.setUserId(event.getRiderId());
            stats.setCityId(cityId);
            stats.setCountryCode(countryCode);
            stats.setDestinationLat(lat);
            stats.setDestinationLng(lng);
            stats.setDestinationAddress(event.getDropoffAddress());
            stats.setTripCount(1);
            stats.setLastTripAt(completedAt);
            destinationStatsRepository.save(stats);
            log.debug(
                    "Created destination stats for rider {} at ({}, {})",
                    event.getRiderId(),
                    lat,
                    lng);
        }
    }

    public DestinationSuggestionsDto getSuggestions(UUID userId, BigDecimal lat, BigDecimal lng) {
        UUID cityId = resolveCityId(lat, lng);

        List<UserDestinationStats> topDestinations =
                destinationStatsRepository.findByUserIdAndCityIdOrderByTripCountDesc(
                        userId, cityId, PageRequest.of(0, MAX_FREQUENT));

        List<FrequentDestinationDto> frequent =
                topDestinations.stream()
                        .map(
                                stats ->
                                        FrequentDestinationDto.builder()
                                                .address(stats.getDestinationAddress())
                                                .latitude(stats.getDestinationLat())
                                                .longitude(stats.getDestinationLng())
                                                .visitCount(stats.getTripCount())
                                                .lastVisitedAt(stats.getLastTripAt())
                                                .build())
                        .toList();

        // Recent rides would come from ride-service via REST call.
        // For now, return empty list -- will be populated when ride-service
        // internal endpoint GET /internal/rides/history is available.
        List<RecentRideDto> recent = List.of();

        return DestinationSuggestionsDto.builder().frequent(frequent).recent(recent).build();
    }

    /**
     * Resolve cityId from coordinates. Uses a hardcoded mapping for now. In production, this would
     * call location-service or country-config-service.
     */
    UUID resolveCityId(BigDecimal lat, BigDecimal lng) {
        // Dar es Salaam approximate bounding box:
        // lat: -7.1 to -6.6, lng: 39.0 to 39.6
        // For now, default everything to Dar es Salaam
        return DAR_ES_SALAAM_CITY_ID;
    }

    static BigDecimal roundTo4Dp(BigDecimal value) {
        return value.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
    }
}
