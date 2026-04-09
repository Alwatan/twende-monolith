package tz.co.twende.analytics.kafka;

import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.analytics.service.EventIngestionService;
import tz.co.twende.common.event.ride.RideCompletedEvent;

@ExtendWith(MockitoExtension.class)
class AnalyticsEventConsumerTest {

    @Mock private EventIngestionService eventIngestionService;

    @InjectMocks private AnalyticsEventConsumer consumer;

    @Test
    void givenValidEvent_whenOnEvent_thenDelegatesToIngestionService() {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setEventType("RIDE_COMPLETED");
        event.setCountryCode("TZ");
        event.setDriverId(UUID.randomUUID());
        event.setTimestamp(Instant.now());

        consumer.onEvent(event);

        verify(eventIngestionService).ingest(event);
    }

    @Test
    void givenIngestionThrowsException_whenOnEvent_thenDoesNotPropagate() {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setEventType("RIDE_COMPLETED");
        event.setTimestamp(Instant.now());

        doThrow(new RuntimeException("DB error")).when(eventIngestionService).ingest(event);

        // Should not throw
        consumer.onEvent(event);

        verify(eventIngestionService).ingest(event);
    }
}
