package tz.co.twende.user.kafka;

import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.user.service.DestinationSuggestionService;

@ExtendWith(MockitoExtension.class)
class RideCompletedConsumerTest {

    @Mock private DestinationSuggestionService destinationSuggestionService;

    @InjectMocks private RideCompletedConsumer consumer;

    @Test
    void givenValidEvent_whenConsumed_thenDelegatesToService() {
        RideCompletedEvent event = createEvent();

        consumer.onRideCompleted(event);

        verify(destinationSuggestionService).onRideCompleted(event);
    }

    @Test
    void givenServiceThrowsException_whenConsumed_thenDoesNotPropagate() {
        RideCompletedEvent event = createEvent();
        doThrow(new RuntimeException("DB error"))
                .when(destinationSuggestionService)
                .onRideCompleted(event);

        // Should not throw
        consumer.onRideCompleted(event);

        verify(destinationSuggestionService).onRideCompleted(event);
    }

    private RideCompletedEvent createEvent() {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setRideId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());
        event.setDriverId(UUID.randomUUID());
        event.setDropoffLat(new BigDecimal("-6.7924"));
        event.setDropoffLng(new BigDecimal("39.2083"));
        event.setDropoffAddress("Mikocheni");
        event.setCountryCode("TZ");
        event.setCompletedAt(Instant.now());
        event.setFinalFare(new BigDecimal("5000"));
        return event;
    }
}
