package tz.co.twende.rating.service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ConflictException;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.rating.client.RideClient;
import tz.co.twende.rating.dto.*;
import tz.co.twende.rating.entity.Rating;
import tz.co.twende.rating.kafka.RatingEventPublisher;
import tz.co.twende.rating.kafka.RideCompletedConsumer;
import tz.co.twende.rating.repository.RatingRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class RatingService {

    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final Duration RATING_WINDOW = Duration.ofHours(48);
    private static final String DRIVER_CACHE_PREFIX = "rating:driver:";
    private static final String RIDER_CACHE_PREFIX = "rating:rider:";

    private final RatingRepository ratingRepository;
    private final RideCompletedConsumer rideCompletedConsumer;
    private final RideClient rideClient;
    private final RatingEventPublisher ratingEventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public RatingDto submitRating(UUID userId, String role, SubmitRatingRequest request) {
        RideDetailsDto ride = resolveRide(request.getRideId());

        if (ride == null || !"COMPLETED".equals(ride.getStatus())) {
            throw new BadRequestException("Can only rate completed rides");
        }

        if (ride.getCompletedAt() != null
                && Instant.now().isAfter(ride.getCompletedAt().plus(RATING_WINDOW))) {
            throw new BadRequestException("Rating window has expired (48 hours)");
        }

        if ("RIDER".equals(role) && !ride.getRiderId().equals(userId)) {
            throw new UnauthorizedException("You are not the rider on this ride");
        }
        if ("DRIVER".equals(role) && !ride.getDriverId().equals(userId)) {
            throw new UnauthorizedException("You are not the driver on this ride");
        }

        if (ratingRepository.existsByRideIdAndRaterRole(request.getRideId(), role)) {
            throw new ConflictException("You have already rated this ride");
        }

        UUID ratedUserId = "RIDER".equals(role) ? ride.getDriverId() : ride.getRiderId();

        Rating rating = new Rating();
        rating.setRideId(request.getRideId());
        rating.setRatedUserId(ratedUserId);
        rating.setRaterUserId(userId);
        rating.setRaterRole(role);
        rating.setScore(request.getScore());
        rating.setComment(request.getComment());
        rating.setCountryCode(ride.getCountryCode());
        ratingRepository.save(rating);

        updateRatingAggregate(ratedUserId, role);

        ratingEventPublisher.publishRatingSubmitted(rating);

        return toDto(rating);
    }

    public RatingSummaryDto getDriverRatingSummary(UUID driverId) {
        return buildSummary(driverId, DRIVER_CACHE_PREFIX);
    }

    public RatingSummaryDto getRiderRatingSummary(UUID riderId) {
        return buildSummary(riderId, RIDER_CACHE_PREFIX);
    }

    public DriverScoreDto getDriverScore(UUID driverId) {
        RatingCacheEntry cached = getCachedAggregate(DRIVER_CACHE_PREFIX + driverId);
        if (cached != null) {
            return DriverScoreDto.builder()
                    .driverId(driverId)
                    .average(cached.getAverage())
                    .count(cached.getCount())
                    .build();
        }

        Double avg = ratingRepository.findAverageScoreByRatedUserId(driverId);
        long count = ratingRepository.countByRatedUserId(driverId);
        double average = avg != null ? avg : 0.0;

        cacheAggregate(DRIVER_CACHE_PREFIX + driverId, average, count);

        return DriverScoreDto.builder().driverId(driverId).average(average).count(count).build();
    }

    private RideDetailsDto resolveRide(UUID rideId) {
        RideDetailsDto cached = rideCompletedConsumer.getCachedRide(rideId);
        if (cached != null) {
            return cached;
        }
        try {
            return rideClient.getRide(rideId);
        } catch (Exception e) {
            log.warn("Failed to fetch ride {} from ride-service: {}", rideId, e.getMessage());
            return null;
        }
    }

    private void updateRatingAggregate(UUID ratedUserId, String raterRole) {
        Double avg = ratingRepository.findAverageScoreByRatedUserId(ratedUserId);
        long count = ratingRepository.countByRatedUserId(ratedUserId);
        double average = avg != null ? avg : 0.0;

        String prefix = "RIDER".equals(raterRole) ? DRIVER_CACHE_PREFIX : RIDER_CACHE_PREFIX;
        cacheAggregate(prefix + ratedUserId, average, count);
    }

    private RatingSummaryDto buildSummary(UUID userId, String cachePrefix) {
        Map<Short, Long> distribution = new LinkedHashMap<>();
        for (short i = 1; i <= 5; i++) {
            distribution.put(i, 0L);
        }
        ratingRepository
                .findScoreDistributionByRatedUserId(userId)
                .forEach(sc -> distribution.put(sc.getScore(), sc.getCnt()));

        Double avg = ratingRepository.findAverageScoreByRatedUserId(userId);
        long count = ratingRepository.countByRatedUserId(userId);
        double average = avg != null ? avg : 0.0;

        cacheAggregate(cachePrefix + userId, average, count);

        return RatingSummaryDto.builder()
                .userId(userId)
                .averageScore(average)
                .totalRatings(count)
                .distribution(distribution)
                .build();
    }

    private void cacheAggregate(String key, double average, long count) {
        try {
            RatingCacheEntry entry = new RatingCacheEntry(average, count, Instant.now());
            redisTemplate.opsForValue().set(key, entry, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Failed to update Redis cache for key {}: {}", key, e.getMessage());
        }
    }

    private RatingCacheEntry getCachedAggregate(String key) {
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof RatingCacheEntry entry) {
                return entry;
            }
        } catch (Exception e) {
            log.warn("Failed to read Redis cache for key {}: {}", key, e.getMessage());
        }
        return null;
    }

    private RatingDto toDto(Rating rating) {
        return RatingDto.builder()
                .id(rating.getId())
                .rideId(rating.getRideId())
                .ratedUserId(rating.getRatedUserId())
                .raterUserId(rating.getRaterUserId())
                .raterRole(rating.getRaterRole())
                .score(rating.getScore())
                .comment(rating.getComment())
                .createdAt(rating.getCreatedAt())
                .build();
    }
}
