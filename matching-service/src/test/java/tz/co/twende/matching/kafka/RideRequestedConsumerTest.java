package tz.co.twende.matching.kafka;

import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.enums.VehicleType;
import tz.co.twende.common.event.Location;
import tz.co.twende.common.event.ride.RideRequestedEvent;
import tz.co.twende.matching.service.MatchingService;

@ExtendWith(MockitoExtension.class)
class RideRequestedConsumerTest {

    @Mock private MatchingService matchingService;

    @InjectMocks private RideRequestedConsumer consumer;

    @Test
    void givenRideRequestedEvent_whenConsumed_thenDelegatedToMatchingService() {
        RideRequestedEvent event = new RideRequestedEvent();
        event.setRideId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());
        event.setCountryCode("TZ");
        event.setVehicleType(VehicleType.BAJAJ);
        event.setPickupLocation(
                new Location(new BigDecimal("-6.8"), new BigDecimal("39.3"), "Test"));
        event.setEstimatedFare(new BigDecimal("5000"));

        consumer.onRideRequested(event);

        verify(matchingService).onRideRequested(event);
    }

    @Test
    void givenMatchingServiceThrows_whenConsumed_thenExceptionHandled() {
        RideRequestedEvent event = new RideRequestedEvent();
        event.setRideId(UUID.randomUUID());

        doThrow(new RuntimeException("Test error")).when(matchingService).onRideRequested(event);

        // Should not throw
        consumer.onRideRequested(event);

        verify(matchingService).onRideRequested(event);
    }
}
