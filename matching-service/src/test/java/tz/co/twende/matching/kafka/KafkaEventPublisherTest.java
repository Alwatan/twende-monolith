package tz.co.twende.matching.kafka;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tz.co.twende.matching.config.KafkaConfig;
import tz.co.twende.matching.dto.OfferPayload;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private KafkaEventPublisher kafkaEventPublisher;

    @Test
    void givenOfferPayload_whenPublishOfferNotification_thenSentToCorrectTopic() {
        OfferPayload payload =
                OfferPayload.builder()
                        .rideId(UUID.randomUUID())
                        .driverId(UUID.randomUUID())
                        .pickupDistanceKm(new BigDecimal("1.2"))
                        .pickupAreaName("Kariakoo")
                        .estimatedTripDistanceKm(new BigDecimal("5.0"))
                        .estimatedTripMinutes(15)
                        .totalFare(new BigDecimal("5000"))
                        .boostAmount(BigDecimal.ZERO)
                        .currencyCode("TZS")
                        .offerWindowSeconds(15)
                        .build();

        kafkaEventPublisher.publishOfferNotification(payload, "TZ", UUID.randomUUID());

        verify(kafkaTemplate)
                .send(eq(KafkaConfig.TOPIC_DRIVERS_OFFER_NOTIFICATION), anyString(), any());
    }

    @Test
    void givenAcceptance_whenPublishOfferAccepted_thenSentToCorrectTopic() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        kafkaEventPublisher.publishOfferAccepted(rideId, driverId, "TZ", 300);

        verify(kafkaTemplate).send(eq(KafkaConfig.TOPIC_RIDES_OFFER_ACCEPTED), anyString(), any());
    }

    @Test
    void givenRejection_whenPublishDriverRejected_thenSentToCorrectTopic() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        kafkaEventPublisher.publishDriverRejected(rideId, driverId, "TZ", 3);

        verify(kafkaTemplate).send(eq(KafkaConfig.TOPIC_DRIVERS_REJECTED_RIDE), anyString(), any());
    }

    @Test
    void givenNullBoostAmount_whenPublishOfferNotification_thenDefaultsToZero() {
        OfferPayload payload =
                OfferPayload.builder()
                        .rideId(UUID.randomUUID())
                        .driverId(UUID.randomUUID())
                        .totalFare(new BigDecimal("3000"))
                        .boostAmount(null)
                        .currencyCode("TZS")
                        .offerWindowSeconds(15)
                        .build();

        kafkaEventPublisher.publishOfferNotification(payload, "TZ", UUID.randomUUID());

        verify(kafkaTemplate)
                .send(eq(KafkaConfig.TOPIC_DRIVERS_OFFER_NOTIFICATION), anyString(), any());
    }
}
