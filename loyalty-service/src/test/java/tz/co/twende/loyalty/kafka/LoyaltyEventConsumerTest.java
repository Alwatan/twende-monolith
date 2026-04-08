package tz.co.twende.loyalty.kafka;

import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.loyalty.service.LoyaltyService;

@ExtendWith(MockitoExtension.class)
class LoyaltyEventConsumerTest {

    @Mock private LoyaltyService loyaltyService;

    @InjectMocks private LoyaltyEventConsumer loyaltyEventConsumer;

    @Test
    void givenRideCompletedEvent_whenConsumed_thenServiceCalled() {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setRideId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());
        event.setCountryCode("TZ");
        event.setVehicleType("BAJAJ");
        event.setDistanceMetres(5000);
        event.setFinalFare(new BigDecimal("3000"));

        loyaltyEventConsumer.onRideCompleted(event);

        verify(loyaltyService).onRideCompleted(event);
    }

    @Test
    void givenServiceThrowsException_whenConsumed_thenExceptionHandledGracefully() {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setRideId(UUID.randomUUID());

        doThrow(new RuntimeException("DB error")).when(loyaltyService).onRideCompleted(event);

        // Should not throw
        loyaltyEventConsumer.onRideCompleted(event);

        verify(loyaltyService).onRideCompleted(event);
    }
}
