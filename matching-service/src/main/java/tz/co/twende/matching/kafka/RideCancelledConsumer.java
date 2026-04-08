package tz.co.twende.matching.kafka;

import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.ride.RideStatusUpdatedEvent;
import tz.co.twende.matching.config.KafkaConfig;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideCancelledConsumer {

    private final StringRedisTemplate stringRedisTemplate;

    @KafkaListener(
            topics = KafkaConfig.TOPIC_RIDES_CANCELLED,
            groupId = "${spring.kafka.consumer.group-id}")
    public void onRideCancelled(RideStatusUpdatedEvent event) {
        UUID rideId = event.getRideId();
        log.info("Ride cancelled: {}", rideId);

        try {
            // Get offered drivers before cleanup
            Set<String> offeredDrivers =
                    stringRedisTemplate.opsForSet().members("rides_offered_to:" + rideId);

            // Clean up all Redis keys for this ride
            stringRedisTemplate.delete("ride_accepted:" + rideId);
            stringRedisTemplate.delete("rides_offered_to:" + rideId);
            stringRedisTemplate.delete("driver_rejected:" + rideId);
            stringRedisTemplate.delete("ride_offer_batches:" + rideId);

            // Delete per-driver dedup keys
            if (offeredDrivers != null) {
                for (String driverId : offeredDrivers) {
                    stringRedisTemplate.delete("driver_offered:" + driverId + ":" + rideId);
                }
            }

            log.info("Cleaned up Redis keys for cancelled ride {}", rideId);
        } catch (Exception e) {
            log.error(
                    "Error cleaning up Redis for cancelled ride {}: {}", rideId, e.getMessage(), e);
        }
    }
}
