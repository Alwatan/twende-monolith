package tz.co.twende.matching.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tz.co.twende.matching.dto.AcceptRejectResponse;
import tz.co.twende.matching.entity.OfferLog;
import tz.co.twende.matching.kafka.KafkaEventPublisher;
import tz.co.twende.matching.repository.OfferLogRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class AcceptanceService {

    private final StringRedisTemplate stringRedisTemplate;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final OfferLogRepository offerLogRepository;

    public AcceptRejectResponse acceptOffer(UUID rideId, UUID driverId, String countryCode) {
        // Atomic lock: SETNX ride_accepted:{rideId} = driverId (60s TTL)
        String lockKey = "ride_accepted:" + rideId;
        Boolean won =
                stringRedisTemplate
                        .opsForValue()
                        .setIfAbsent(lockKey, driverId.toString(), Duration.ofSeconds(60));

        if (Boolean.TRUE.equals(won)) {
            // Won the acceptance race
            kafkaEventPublisher.publishOfferAccepted(rideId, driverId, countryCode, 300);

            // Update driver stats
            String statsKey = "driver_stats:" + driverId;
            stringRedisTemplate.opsForHash().increment(statsKey, "accepted_count", 1);
            recomputeAcceptanceRate(driverId);

            // Update offer log
            updateOfferLogResponse(rideId, driverId, "ACCEPTED");

            log.info("Driver {} accepted ride {} (won race)", driverId, rideId);
            return AcceptRejectResponse.builder()
                    .rideId(rideId)
                    .driverId(driverId)
                    .action("ACCEPT")
                    .success(true)
                    .message("Ride accepted successfully")
                    .build();
        } else {
            // Lost the race
            updateOfferLogResponse(rideId, driverId, "RIDE_TAKEN");

            log.info("Driver {} tried to accept ride {} but lost race", driverId, rideId);
            return AcceptRejectResponse.builder()
                    .rideId(rideId)
                    .driverId(driverId)
                    .action("ACCEPT")
                    .success(false)
                    .message("Ride already accepted by another driver")
                    .build();
        }
    }

    public AcceptRejectResponse rejectOffer(UUID rideId, UUID driverId, String countryCode) {
        // Track rejection for this ride (prevents re-offer, even on fare boost)
        String rejectedKey = "driver_rejected:" + rideId;
        stringRedisTemplate.opsForSet().add(rejectedKey, driverId.toString());
        stringRedisTemplate.expire(rejectedKey, 300, TimeUnit.SECONDS);

        // Update driver stats
        String statsKey = "driver_stats:" + driverId;
        stringRedisTemplate.opsForHash().increment(statsKey, "rejection_count", 1);
        recomputeAcceptanceRate(driverId);

        // Get new rejection count for this ride
        Set<String> rejections = stringRedisTemplate.opsForSet().members(rejectedKey);
        int newRejectionCount = rejections != null ? rejections.size() : 1;

        // Update offer log
        updateOfferLogResponse(rideId, driverId, "REJECTED");

        // Publish event
        kafkaEventPublisher.publishDriverRejected(rideId, driverId, countryCode, newRejectionCount);

        log.info("Driver {} rejected ride {}", driverId, rideId);
        return AcceptRejectResponse.builder()
                .rideId(rideId)
                .driverId(driverId)
                .action("REJECT")
                .success(true)
                .message("Offer rejected")
                .build();
    }

    void recomputeAcceptanceRate(UUID driverId) {
        String key = "driver_stats:" + driverId;
        Object offeredObj = stringRedisTemplate.opsForHash().get(key, "offered_count");
        Object acceptedObj = stringRedisTemplate.opsForHash().get(key, "accepted_count");
        long offered = offeredObj != null ? Long.parseLong(offeredObj.toString()) : 0;
        long accepted = acceptedObj != null ? Long.parseLong(acceptedObj.toString()) : 0;
        double rate = offered > 0 ? (double) accepted / offered : 0.0;
        stringRedisTemplate.opsForHash().put(key, "acceptance_rate", String.valueOf(rate));
    }

    private void updateOfferLogResponse(UUID rideId, UUID driverId, String response) {
        Optional<OfferLog> offerLogOpt =
                offerLogRepository.findByRideIdAndDriverId(rideId, driverId);
        offerLogOpt.ifPresent(
                offerLog -> {
                    offerLog.setResponse(response);
                    offerLog.setRespondedAt(Instant.now());
                    offerLogRepository.save(offerLog);
                });
    }
}
