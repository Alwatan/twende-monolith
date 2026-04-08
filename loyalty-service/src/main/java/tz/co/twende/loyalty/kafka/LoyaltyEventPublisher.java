package tz.co.twende.loyalty.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.loyalty.FreeRideOfferEarnedEvent;
import tz.co.twende.loyalty.config.KafkaConfig;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoyaltyEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishFreeRideEarned(FreeRideOfferEarnedEvent event) {
        String key = event.getCountryCode() + ":" + event.getOfferId();
        kafkaTemplate.send(KafkaConfig.TOPIC_FREE_RIDE_EARNED, key, event);
        log.info(
                "Published FreeRideOfferEarnedEvent for offer {} to rider {}",
                event.getOfferId(),
                event.getRiderId());
    }
}
