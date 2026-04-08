package tz.co.twende.user.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.user.service.DestinationSuggestionService;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideCompletedConsumer {

    private final DestinationSuggestionService destinationSuggestionService;

    @KafkaListener(topics = "twende.rides.completed", groupId = "user-service-group")
    public void onRideCompleted(RideCompletedEvent event) {
        log.debug("Received RideCompletedEvent for ride {}", event.getRideId());
        try {
            destinationSuggestionService.onRideCompleted(event);
        } catch (Exception e) {
            log.error(
                    "Failed to process RideCompletedEvent for ride {}: {}",
                    event.getRideId(),
                    e.getMessage(),
                    e);
        }
    }
}
