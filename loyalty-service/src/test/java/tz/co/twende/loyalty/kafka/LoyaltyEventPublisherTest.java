package tz.co.twende.loyalty.kafka;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tz.co.twende.common.enums.VehicleType;
import tz.co.twende.common.event.loyalty.FreeRideOfferEarnedEvent;
import tz.co.twende.loyalty.config.KafkaConfig;

@ExtendWith(MockitoExtension.class)
class LoyaltyEventPublisherTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private LoyaltyEventPublisher loyaltyEventPublisher;

    @Test
    void givenFreeRideEvent_whenPublished_thenSentToCorrectTopic() {
        UUID offerId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();

        FreeRideOfferEarnedEvent event = new FreeRideOfferEarnedEvent();
        event.setOfferId(offerId);
        event.setRiderId(riderId);
        event.setVehicleType(VehicleType.BAJAJ);
        event.setMaxDistanceKm(new BigDecimal("5.00"));
        event.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        event.setCountryCode("TZ");

        loyaltyEventPublisher.publishFreeRideEarned(event);

        String expectedKey = "TZ:" + offerId;
        verify(kafkaTemplate)
                .send(eq(KafkaConfig.TOPIC_FREE_RIDE_EARNED), eq(expectedKey), eq(event));
    }
}
