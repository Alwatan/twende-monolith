package tz.co.twende.matching.kafka;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.ride.RideFareBoostedEvent;
import tz.co.twende.matching.client.LocationServiceClient;
import tz.co.twende.matching.config.KafkaConfig;
import tz.co.twende.matching.service.BroadcastService;
import tz.co.twende.matching.service.DriverScoringService;
import tz.co.twende.matching.service.MatchingService;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideFareBoostedConsumer {

    private final LocationServiceClient locationServiceClient;
    private final MatchingService matchingService;
    private final DriverScoringService driverScoringService;
    private final BroadcastService broadcastService;
    private final StringRedisTemplate stringRedisTemplate;

    @KafkaListener(
            topics = KafkaConfig.TOPIC_RIDES_FARE_BOOSTED,
            groupId = "${spring.kafka.consumer.group-id}")
    public void onFareBoosted(RideFareBoostedEvent event) {
        UUID rideId = event.getRideId();
        log.info(
                "Fare boosted for ride {} from {} to {}",
                rideId,
                event.getPreviousFare(),
                event.getNewFare());

        try {
            // Re-broadcast to un-offered drivers with updated fare
            // Note: We don't have full ride context here, so we use a default radius
            // In production, this would fetch the ride details from ride-service
            String batchStr = stringRedisTemplate.opsForValue().get("ride_offer_batches:" + rideId);
            int currentBatch = batchStr != null ? Integer.parseInt(batchStr) : 1;

            // We cannot re-broadcast without location context from the original ride
            // This consumer logs the event; actual re-broadcast would need ride details
            log.info(
                    "Fare boost processed for ride {} (batch {}). Re-broadcast requires ride"
                            + " context.",
                    rideId,
                    currentBatch);
        } catch (Exception e) {
            log.error("Error processing fare boost for ride {}: {}", rideId, e.getMessage(), e);
        }
    }
}
