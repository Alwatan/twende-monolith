package tz.co.twende.matching.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tz.co.twende.matching.dto.DriverCandidate;
import tz.co.twende.matching.dto.OfferPayload;
import tz.co.twende.matching.entity.OfferLog;
import tz.co.twende.matching.kafka.KafkaEventPublisher;
import tz.co.twende.matching.repository.OfferLogRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcastService {

    private final StringRedisTemplate stringRedisTemplate;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final OfferLogRepository offerLogRepository;

    @Value("${twende.matching.offer-window-seconds:15}")
    private int offerWindowSeconds;

    public void broadcastOffers(
            UUID rideId,
            UUID riderId,
            String countryCode,
            List<DriverCandidate> candidates,
            int batchNumber,
            BigDecimal totalFare,
            BigDecimal boostAmount,
            String currencyCode,
            String pickupAreaName,
            BigDecimal estimatedTripDistanceKm,
            int estimatedTripMinutes) {

        for (DriverCandidate candidate : candidates) {
            UUID driverId = candidate.getDriverId();

            // Dedup: SETNX driver_offered:{driverId}:{rideId} with 20s TTL
            String dedupKey = "driver_offered:" + driverId + ":" + rideId;
            Boolean isNew =
                    stringRedisTemplate
                            .opsForValue()
                            .setIfAbsent(dedupKey, "1", Duration.ofSeconds(20));

            if (Boolean.TRUE.equals(isNew)) {
                // Track who has been offered
                String offeredKey = "rides_offered_to:" + rideId;
                stringRedisTemplate.opsForSet().add(offeredKey, driverId.toString());
                stringRedisTemplate.expire(offeredKey, 300, TimeUnit.SECONDS);

                // Update driver stats
                stringRedisTemplate
                        .opsForHash()
                        .increment("driver_stats:" + driverId, "offered_count", 1);

                // Build offer payload
                OfferPayload payload =
                        OfferPayload.builder()
                                .rideId(rideId)
                                .driverId(driverId)
                                .pickupDistanceKm(candidate.getDistanceKm())
                                .pickupAreaName(pickupAreaName)
                                .estimatedTripDistanceKm(estimatedTripDistanceKm)
                                .estimatedTripMinutes(estimatedTripMinutes)
                                .totalFare(totalFare)
                                .boostAmount(boostAmount != null ? boostAmount : BigDecimal.ZERO)
                                .currencyCode(currencyCode)
                                .offerWindowSeconds(offerWindowSeconds)
                                .build();

                // Publish notification event
                kafkaEventPublisher.publishOfferNotification(payload, countryCode, riderId);

                // Save offer log
                OfferLog offerLog = new OfferLog();
                offerLog.setCountryCode(countryCode);
                offerLog.setRideId(rideId);
                offerLog.setDriverId(driverId);
                offerLog.setBatchNumber(batchNumber);
                offerLog.setDistanceKm(candidate.getDistanceKm());
                offerLog.setScore(candidate.getCompositeScore());
                offerLog.setOfferedAt(Instant.now());
                offerLogRepository.save(offerLog);

                log.debug(
                        "Broadcast offer for ride {} to driver {} (batch {})",
                        rideId,
                        driverId,
                        batchNumber);
            }
        }
    }
}
