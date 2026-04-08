package tz.co.twende.loyalty.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.loyalty.config.KafkaConfig;
import tz.co.twende.loyalty.service.LoyaltyService;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoyaltyEventConsumer {

    private final LoyaltyService loyaltyService;

    @KafkaListener(topics = KafkaConfig.TOPIC_RIDES_COMPLETED, groupId = "loyalty-service-group")
    public void onRideCompleted(RideCompletedEvent event) {
        log.debug("Received RideCompletedEvent for ride {}", event.getRideId());
        try {
            loyaltyService.onRideCompleted(event);
        } catch (Exception e) {
            log.error(
                    "Failed to process RideCompletedEvent for ride {}: {}",
                    event.getRideId(),
                    e.getMessage(),
                    e);
        }
    }
}
