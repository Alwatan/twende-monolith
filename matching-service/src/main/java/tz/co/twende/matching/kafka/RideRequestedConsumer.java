package tz.co.twende.matching.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.ride.RideRequestedEvent;
import tz.co.twende.matching.config.KafkaConfig;
import tz.co.twende.matching.service.MatchingService;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideRequestedConsumer {

    private final MatchingService matchingService;

    @KafkaListener(
            topics = KafkaConfig.TOPIC_RIDES_REQUESTED,
            groupId = "${spring.kafka.consumer.group-id}")
    public void onRideRequested(RideRequestedEvent event) {
        log.info("Received ride requested event for ride {}", event.getRideId());
        try {
            matchingService.onRideRequested(event);
        } catch (Exception e) {
            log.error("Error processing ride request {}: {}", event.getRideId(), e.getMessage(), e);
        }
    }
}
