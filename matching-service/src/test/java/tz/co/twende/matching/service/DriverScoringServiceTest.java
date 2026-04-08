package tz.co.twende.matching.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import tz.co.twende.matching.client.RatingServiceClient;
import tz.co.twende.matching.dto.DriverCandidate;
import tz.co.twende.matching.dto.NearbyDriverDto;

@ExtendWith(MockitoExtension.class)
class DriverScoringServiceTest {

    @Mock private RatingServiceClient ratingServiceClient;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks private DriverScoringService driverScoringService;

    @Test
    void givenCloseHighRatedDriver_whenComputeScore_thenHighScore() {
        BigDecimal distance = new BigDecimal("0.5");
        BigDecimal rating = new BigDecimal("5.0");
        BigDecimal acceptance = new BigDecimal("0.9");
        double radius = 3.0;

        BigDecimal score = driverScoringService.computeScore(distance, rating, acceptance, radius);

        // distance = 1 - 0.5/3.0 = 0.833, * 0.50 = 0.417
        // rating = (5-1)/4 = 1.0, * 0.30 = 0.300
        // acceptance = 0.9, * 0.20 = 0.180
        // total ~ 0.897
        assertThat(score).isGreaterThan(new BigDecimal("0.8"));
    }

    @Test
    void givenFarLowRatedDriver_whenComputeScore_thenLowScore() {
        BigDecimal distance = new BigDecimal("2.8");
        BigDecimal rating = new BigDecimal("1.5");
        BigDecimal acceptance = new BigDecimal("0.1");
        double radius = 3.0;

        BigDecimal score = driverScoringService.computeScore(distance, rating, acceptance, radius);

        // distance = 1 - 2.8/3.0 = 0.067, * 0.50 = 0.033
        // rating = (1.5-1)/4 = 0.125, * 0.30 = 0.038
        // acceptance = 0.1, * 0.20 = 0.020
        // total ~ 0.091
        assertThat(score).isLessThan(new BigDecimal("0.2"));
    }

    @Test
    void givenDriverAtExactRadius_whenComputeScore_thenDistanceScoreIsZero() {
        BigDecimal distance = new BigDecimal("5.0");
        BigDecimal rating = new BigDecimal("4.0");
        BigDecimal acceptance = new BigDecimal("0.5");
        double radius = 5.0;

        BigDecimal score = driverScoringService.computeScore(distance, rating, acceptance, radius);

        // distance = 1 - 5/5 = 0, * 0.50 = 0
        // rating = (4-1)/4 = 0.75, * 0.30 = 0.225
        // acceptance = 0.5, * 0.20 = 0.100
        // total ~ 0.325
        assertThat(score.doubleValue()).isCloseTo(0.325, within(0.01));
    }

    @Test
    void givenDriverBeyondRadius_whenComputeScore_thenDistanceScoreClampedToZero() {
        BigDecimal distance = new BigDecimal("6.0");
        BigDecimal rating = new BigDecimal("3.0");
        BigDecimal acceptance = new BigDecimal("0.5");
        double radius = 5.0;

        BigDecimal score = driverScoringService.computeScore(distance, rating, acceptance, radius);

        // distance = max(0, 1 - 6/5) = 0, * 0.50 = 0
        // rating = (3-1)/4 = 0.5, * 0.30 = 0.150
        // acceptance = 0.5, * 0.20 = 0.100
        // total ~ 0.250
        assertThat(score.doubleValue()).isCloseTo(0.250, within(0.01));
    }

    @Test
    void givenMultipleDrivers_whenScoreAndRank_thenSortedByCompositeScoreDescending() {
        UUID driver1 = UUID.randomUUID();
        UUID driver2 = UUID.randomUUID();
        UUID driver3 = UUID.randomUUID();

        NearbyDriverDto nearby1 =
                NearbyDriverDto.builder()
                        .driverId(driver1)
                        .distanceKm(new BigDecimal("0.5"))
                        .build();
        NearbyDriverDto nearby2 =
                NearbyDriverDto.builder()
                        .driverId(driver2)
                        .distanceKm(new BigDecimal("2.0"))
                        .build();
        NearbyDriverDto nearby3 =
                NearbyDriverDto.builder()
                        .driverId(driver3)
                        .distanceKm(new BigDecimal("1.0"))
                        .build();

        when(ratingServiceClient.getDriverAverageRating(driver1)).thenReturn(new BigDecimal("4.5"));
        when(ratingServiceClient.getDriverAverageRating(driver2)).thenReturn(new BigDecimal("4.0"));
        when(ratingServiceClient.getDriverAverageRating(driver3)).thenReturn(new BigDecimal("4.8"));

        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("driver_stats:" + driver1, "acceptance_rate")).thenReturn("0.8");
        when(hashOperations.get("driver_stats:" + driver2, "acceptance_rate")).thenReturn("0.6");
        when(hashOperations.get("driver_stats:" + driver3, "acceptance_rate")).thenReturn("0.9");

        List<DriverCandidate> ranked =
                driverScoringService.scoreAndRank(List.of(nearby1, nearby2, nearby3), 3.0);

        assertThat(ranked).hasSize(3);
        // Driver 1 (closest, good rating) should be first
        assertThat(ranked.get(0).getDriverId()).isEqualTo(driver1);
        // Scores should be in descending order
        assertThat(ranked.get(0).getCompositeScore())
                .isGreaterThanOrEqualTo(ranked.get(1).getCompositeScore());
        assertThat(ranked.get(1).getCompositeScore())
                .isGreaterThanOrEqualTo(ranked.get(2).getCompositeScore());
    }

    @Test
    void givenPerfectScore_whenComputeScore_thenMaxValue() {
        BigDecimal distance = BigDecimal.ZERO;
        BigDecimal rating = new BigDecimal("5.0");
        BigDecimal acceptance = BigDecimal.ONE;
        double radius = 3.0;

        BigDecimal score = driverScoringService.computeScore(distance, rating, acceptance, radius);

        // distance = 1.0, * 0.50 = 0.500
        // rating = 1.0, * 0.30 = 0.300
        // acceptance = 1.0, * 0.20 = 0.200
        // total = 1.000
        assertThat(score).isEqualByComparingTo(BigDecimal.ONE);
    }
}
