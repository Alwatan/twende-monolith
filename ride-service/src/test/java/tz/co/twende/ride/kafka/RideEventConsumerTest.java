package tz.co.twende.ride.kafka;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.event.driver.DriverRejectedRideEvent;
import tz.co.twende.common.event.driver.RideOfferAcceptedEvent;
import tz.co.twende.ride.service.RideService;

@ExtendWith(MockitoExtension.class)
class RideEventConsumerTest {

    @Mock private RideService rideService;

    @InjectMocks private RideEventConsumer consumer;

    @Test
    void givenOfferAcceptedEvent_whenConsumed_thenAssignDriverCalled() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        RideOfferAcceptedEvent event = new RideOfferAcceptedEvent();
        event.setRideId(rideId);
        event.setDriverId(driverId);
        event.setEstimatedArrivalSeconds(120);

        consumer.onOfferAccepted(event);

        verify(rideService).assignDriver(rideId, driverId, 120);
    }

    @Test
    void givenOfferAcceptedEventWithError_whenConsumed_thenErrorHandled() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        RideOfferAcceptedEvent event = new RideOfferAcceptedEvent();
        event.setRideId(rideId);
        event.setDriverId(driverId);
        event.setEstimatedArrivalSeconds(120);

        doThrow(new RuntimeException("Test error"))
                .when(rideService)
                .assignDriver(rideId, driverId, 120);

        // Should not throw
        consumer.onOfferAccepted(event);
    }

    @Test
    void givenDriverRejectedEvent_whenConsumed_thenHandleRejectionCalled() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        DriverRejectedRideEvent event = new DriverRejectedRideEvent();
        event.setRideId(rideId);
        event.setDriverId(driverId);

        consumer.onDriverRejected(event);

        verify(rideService).handleDriverRejection(rideId, driverId);
    }

    @Test
    void givenDriverRejectedEventWithError_whenConsumed_thenErrorHandled() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        DriverRejectedRideEvent event = new DriverRejectedRideEvent();
        event.setRideId(rideId);
        event.setDriverId(driverId);

        doThrow(new RuntimeException("Test error"))
                .when(rideService)
                .handleDriverRejection(rideId, driverId);

        // Should not throw
        consumer.onDriverRejected(event);
    }
}
