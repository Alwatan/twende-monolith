package tz.co.twende.matching.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tz.co.twende.matching.client.RatingServiceClient;
import tz.co.twende.matching.dto.DriverCandidate;
import tz.co.twende.matching.dto.NearbyDriverDto;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverScoringService {

    private static final BigDecimal DISTANCE_WEIGHT = new BigDecimal("0.50");
    private static final BigDecimal RATING_WEIGHT = new BigDecimal("0.30");
    private static final BigDecimal ACCEPTANCE_WEIGHT = new BigDecimal("0.20");
    private static final BigDecimal DEFAULT_ACCEPTANCE_RATE = new BigDecimal("0.5");

    private final RatingServiceClient ratingServiceClient;
    private final StringRedisTemplate stringRedisTemplate;

    public List<DriverCandidate> scoreAndRank(
            List<NearbyDriverDto> nearbyDrivers, double radiusKm) {
        return nearbyDrivers.stream()
                .map(driver -> buildCandidate(driver, radiusKm))
                .sorted(Comparator.comparing(DriverCandidate::getCompositeScore).reversed())
                .toList();
    }

    DriverCandidate buildCandidate(NearbyDriverDto driver, double radiusKm) {
        BigDecimal distanceKm = driver.getDistanceKm();
        BigDecimal ratingScore = ratingServiceClient.getDriverAverageRating(driver.getDriverId());
        BigDecimal acceptanceRate = getAcceptanceRate(driver.getDriverId());

        BigDecimal compositeScore = computeScore(distanceKm, ratingScore, acceptanceRate, radiusKm);

        return DriverCandidate.builder()
                .driverId(driver.getDriverId())
                .distanceKm(distanceKm)
                .ratingScore(ratingScore)
                .acceptanceRate(acceptanceRate)
                .compositeScore(compositeScore)
                .build();
    }

    public BigDecimal computeScore(
            BigDecimal distanceKm,
            BigDecimal ratingScore,
            BigDecimal acceptanceRate,
            double radiusKm) {
        BigDecimal radiusBd = BigDecimal.valueOf(radiusKm);

        // Distance score: closer is better (1.0 for 0km, 0.0 for radiusKm)
        BigDecimal distanceScore =
                BigDecimal.ONE.subtract(distanceKm.divide(radiusBd, 6, RoundingMode.HALF_UP));
        if (distanceScore.compareTo(BigDecimal.ZERO) < 0) {
            distanceScore = BigDecimal.ZERO;
        }

        // Rating score: normalised 0-1 (rating is 1-5 scale)
        BigDecimal ratingNorm =
                ratingScore
                        .subtract(BigDecimal.ONE)
                        .divide(new BigDecimal("4.0"), 6, RoundingMode.HALF_UP);

        // Acceptance rate score: already 0-1
        BigDecimal acceptanceScore = acceptanceRate;

        // Weighted sum
        BigDecimal score =
                distanceScore
                        .multiply(DISTANCE_WEIGHT)
                        .add(ratingNorm.multiply(RATING_WEIGHT))
                        .add(acceptanceScore.multiply(ACCEPTANCE_WEIGHT));

        return score.setScale(3, RoundingMode.HALF_UP);
    }

    BigDecimal getAcceptanceRate(UUID driverId) {
        try {
            String key = "driver_stats:" + driverId;
            Object rateVal = stringRedisTemplate.opsForHash().get(key, "acceptance_rate");
            if (rateVal != null) {
                return new BigDecimal(rateVal.toString());
            }
        } catch (Exception e) {
            log.warn("Error reading acceptance rate for driver {}: {}", driverId, e.getMessage());
        }
        return DEFAULT_ACCEPTANCE_RATE;
    }
}
