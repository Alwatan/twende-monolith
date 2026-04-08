package tz.co.twende.matching.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.ride.BookingRequestedEvent;
import tz.co.twende.matching.config.KafkaConfig;
import tz.co.twende.matching.service.MarketplaceService;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingRequestedConsumer {

    private final MarketplaceService marketplaceService;

    @KafkaListener(
            topics = KafkaConfig.TOPIC_BOOKING_REQUESTED,
            groupId = "${spring.kafka.consumer.group-id}")
    public void onBookingRequested(BookingRequestedEvent event) {
        log.info("Received booking requested event for booking {}", event.getBookingId());
        try {
            marketplaceService.addBookingToMarketplace(event);
        } catch (Exception e) {
            log.error(
                    "Error processing booking request {}: {}",
                    event.getBookingId(),
                    e.getMessage(),
                    e);
        }
    }
}
