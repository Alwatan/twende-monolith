package tz.co.twende.rating.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ConflictException;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.rating.client.RideClient;
import tz.co.twende.rating.dto.*;
import tz.co.twende.rating.entity.Rating;
import tz.co.twende.rating.kafka.RatingEventPublisher;
import tz.co.twende.rating.kafka.RideCompletedConsumer;
import tz.co.twende.rating.repository.RatingRepository;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock private RatingRepository ratingRepository;
    @Mock private RideCompletedConsumer rideCompletedConsumer;
    @Mock private RideClient rideClient;
    @Mock private RatingEventPublisher ratingEventPublisher;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks private RatingService ratingService;

    @Test
    void givenCompletedRide_whenRiderSubmitsRating_thenRatingStored() {
        UUID riderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        RideDetailsDto ride = completedRide(rideId, riderId, driverId);
        when(rideCompletedConsumer.getCachedRide(rideId)).thenReturn(ride);
        when(ratingRepository.existsByRideIdAndRaterRole(rideId, "RIDER")).thenReturn(false);
        when(ratingRepository.save(any(Rating.class)))
                .thenAnswer(
                        inv -> {
                            Rating r = inv.getArgument(0);
                            r.setId(UUID.randomUUID());
                            r.setCreatedAt(Instant.now());
                            return r;
                        });
        when(ratingRepository.findAverageScoreByRatedUserId(driverId)).thenReturn(4.5);
        when(ratingRepository.countByRatedUserId(driverId)).thenReturn(10L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        SubmitRatingRequest request = new SubmitRatingRequest(rideId, (short) 5, "Great driver!");

        RatingDto result = ratingService.submitRating(riderId, "RIDER", request);

        assertThat(result.getScore()).isEqualTo((short) 5);
        assertThat(result.getRatedUserId()).isEqualTo(driverId);
        assertThat(result.getRaterRole()).isEqualTo("RIDER");

        ArgumentCaptor<Rating> captor = ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepository).save(captor.capture());
        assertThat(captor.getValue().getRatedUserId()).isEqualTo(driverId);
        verify(ratingEventPublisher).publishRatingSubmitted(any(Rating.class));
    }

    @Test
    void givenCompletedRide_whenDriverSubmitsRating_thenRiderIsRated() {
        UUID riderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        RideDetailsDto ride = completedRide(rideId, riderId, driverId);
        when(rideCompletedConsumer.getCachedRide(rideId)).thenReturn(ride);
        when(ratingRepository.existsByRideIdAndRaterRole(rideId, "DRIVER")).thenReturn(false);
        when(ratingRepository.save(any(Rating.class)))
                .thenAnswer(
                        inv -> {
                            Rating r = inv.getArgument(0);
                            r.setId(UUID.randomUUID());
                            r.setCreatedAt(Instant.now());
                            return r;
                        });
        when(ratingRepository.findAverageScoreByRatedUserId(riderId)).thenReturn(4.0);
        when(ratingRepository.countByRatedUserId(riderId)).thenReturn(5L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        SubmitRatingRequest request = new SubmitRatingRequest(rideId, (short) 4, null);

        RatingDto result = ratingService.submitRating(driverId, "DRIVER", request);

        assertThat(result.getRatedUserId()).isEqualTo(riderId);
        assertThat(result.getRaterRole()).isEqualTo("DRIVER");
    }

    @Test
    void givenNonCompletedRide_whenSubmitRating_thenThrowsBadRequest() {
        UUID userId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        RideDetailsDto ride = new RideDetailsDto();
        ride.setId(rideId);
        ride.setStatus("IN_PROGRESS");
        when(rideCompletedConsumer.getCachedRide(rideId)).thenReturn(ride);

        SubmitRatingRequest request = new SubmitRatingRequest(rideId, (short) 5, null);

        assertThatThrownBy(() -> ratingService.submitRating(userId, "RIDER", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("completed");
    }

    @Test
    void givenNullRide_whenSubmitRating_thenThrowsBadRequest() {
        UUID userId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        when(rideCompletedConsumer.getCachedRide(rideId)).thenReturn(null);
        when(rideClient.getRide(rideId)).thenReturn(null);

        SubmitRatingRequest request = new SubmitRatingRequest(rideId, (short) 5, null);

        assertThatThrownBy(() -> ratingService.submitRating(userId, "RIDER", request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void givenRiderNotOnRide_whenSubmitRating_thenThrowsUnauthorized() {
        UUID riderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID wrongUserId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        RideDetailsDto ride = completedRide(rideId, riderId, driverId);
        when(rideCompletedConsumer.getCachedRide(rideId)).thenReturn(ride);

        SubmitRatingRequest request = new SubmitRatingRequest(rideId, (short) 5, null);

        assertThatThrownBy(() -> ratingService.submitRating(wrongUserId, "RIDER", request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("rider");
    }

    @Test
    void givenDriverNotOnRide_whenSubmitRating_thenThrowsUnauthorized() {
        UUID riderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID wrongUserId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        RideDetailsDto ride = completedRide(rideId, riderId, driverId);
        when(rideCompletedConsumer.getCachedRide(rideId)).thenReturn(ride);

        SubmitRatingRequest request = new SubmitRatingRequest(rideId, (short) 5, null);

        assertThatThrownBy(() -> ratingService.submitRating(wrongUserId, "DRIVER", request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("driver");
    }

    @Test
    void givenExistingRating_whenSameRoleRatesAgain_thenThrowsConflict() {
        UUID riderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        RideDetailsDto ride = completedRide(rideId, riderId, driverId);
        when(rideCompletedConsumer.getCachedRide(rideId)).thenReturn(ride);
        when(ratingRepository.existsByRideIdAndRaterRole(rideId, "RIDER")).thenReturn(true);

        SubmitRatingRequest request = new SubmitRatingRequest(rideId, (short) 5, null);

        assertThatThrownBy(() -> ratingService.submitRating(riderId, "RIDER", request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already rated");
    }

    @Test
    void givenExpiredWindow_whenSubmitRating_thenThrowsBadRequest() {
        UUID riderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        RideDetailsDto ride = completedRide(rideId, riderId, driverId);
        ride.setCompletedAt(Instant.now().minus(Duration.ofHours(49)));
        when(rideCompletedConsumer.getCachedRide(rideId)).thenReturn(ride);

        SubmitRatingRequest request = new SubmitRatingRequest(rideId, (short) 5, null);

        assertThatThrownBy(() -> ratingService.submitRating(riderId, "RIDER", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void givenCachedAggregate_whenGetDriverScore_thenReturnCached() {
        UUID driverId = UUID.randomUUID();
        RatingCacheEntry cached = new RatingCacheEntry(4.5, 100, Instant.now());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("rating:driver:" + driverId)).thenReturn(cached);

        DriverScoreDto score = ratingService.getDriverScore(driverId);

        assertThat(score.getAverage()).isEqualTo(4.5);
        assertThat(score.getCount()).isEqualTo(100);
        verify(ratingRepository, never()).findAverageScoreByRatedUserId(any());
    }

    @Test
    void givenNoCachedAggregate_whenGetDriverScore_thenComputeFromDb() {
        UUID driverId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("rating:driver:" + driverId)).thenReturn(null);
        when(ratingRepository.findAverageScoreByRatedUserId(driverId)).thenReturn(3.8);
        when(ratingRepository.countByRatedUserId(driverId)).thenReturn(50L);

        DriverScoreDto score = ratingService.getDriverScore(driverId);

        assertThat(score.getAverage()).isEqualTo(3.8);
        assertThat(score.getCount()).isEqualTo(50);
        verify(valueOperations).set(eq("rating:driver:" + driverId), any(), any(Duration.class));
    }

    @Test
    void givenNoRatings_whenGetDriverScore_thenReturnZero() {
        UUID driverId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("rating:driver:" + driverId)).thenReturn(null);
        when(ratingRepository.findAverageScoreByRatedUserId(driverId)).thenReturn(null);
        when(ratingRepository.countByRatedUserId(driverId)).thenReturn(0L);

        DriverScoreDto score = ratingService.getDriverScore(driverId);

        assertThat(score.getAverage()).isEqualTo(0.0);
        assertThat(score.getCount()).isZero();
    }

    @Test
    void givenMultipleRatings_whenGetDriverSummary_thenCorrectDistribution() {
        UUID driverId = UUID.randomUUID();
        when(ratingRepository.findAverageScoreByRatedUserId(driverId)).thenReturn(4.2);
        when(ratingRepository.countByRatedUserId(driverId)).thenReturn(5L);
        when(ratingRepository.findScoreDistributionByRatedUserId(driverId))
                .thenReturn(
                        List.of(
                                mockScoreCount((short) 3, 1L),
                                mockScoreCount((short) 4, 2L),
                                mockScoreCount((short) 5, 2L)));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        RatingSummaryDto summary = ratingService.getDriverRatingSummary(driverId);

        assertThat(summary.getAverageScore()).isEqualTo(4.2);
        assertThat(summary.getTotalRatings()).isEqualTo(5L);
        assertThat(summary.getDistribution()).containsEntry((short) 1, 0L);
        assertThat(summary.getDistribution()).containsEntry((short) 3, 1L);
        assertThat(summary.getDistribution()).containsEntry((short) 5, 2L);
    }

    @Test
    void givenRideFallsBackToClient_whenCacheEmpty_thenUsesRestClient() {
        UUID riderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        when(rideCompletedConsumer.getCachedRide(rideId)).thenReturn(null);
        RideDetailsDto ride = completedRide(rideId, riderId, driverId);
        when(rideClient.getRide(rideId)).thenReturn(ride);
        when(ratingRepository.existsByRideIdAndRaterRole(rideId, "RIDER")).thenReturn(false);
        when(ratingRepository.save(any(Rating.class)))
                .thenAnswer(
                        inv -> {
                            Rating r = inv.getArgument(0);
                            r.setId(UUID.randomUUID());
                            r.setCreatedAt(Instant.now());
                            return r;
                        });
        when(ratingRepository.findAverageScoreByRatedUserId(driverId)).thenReturn(5.0);
        when(ratingRepository.countByRatedUserId(driverId)).thenReturn(1L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        SubmitRatingRequest request = new SubmitRatingRequest(rideId, (short) 5, null);
        RatingDto result = ratingService.submitRating(riderId, "RIDER", request);

        assertThat(result.getScore()).isEqualTo((short) 5);
        verify(rideClient).getRide(rideId);
    }

    private RideDetailsDto completedRide(UUID rideId, UUID riderId, UUID driverId) {
        RideDetailsDto ride = new RideDetailsDto();
        ride.setId(rideId);
        ride.setRiderId(riderId);
        ride.setDriverId(driverId);
        ride.setStatus("COMPLETED");
        ride.setCountryCode("TZ");
        ride.setCompletedAt(Instant.now().minus(Duration.ofMinutes(30)));
        return ride;
    }

    private RatingRepository.ScoreCount mockScoreCount(short score, long count) {
        return new RatingRepository.ScoreCount() {
            @Override
            public Short getScore() {
                return score;
            }

            @Override
            public Long getCnt() {
                return count;
            }
        };
    }
}
