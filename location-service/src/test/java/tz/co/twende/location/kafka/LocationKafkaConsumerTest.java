package tz.co.twende.location.kafka;

import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.enums.DriverStatus;
import tz.co.twende.common.event.driver.DriverStatusUpdatedEvent;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.location.service.TripTraceService;

@ExtendWith(MockitoExtension.class)
class LocationKafkaConsumerTest {

    @Mock private TripTraceService tripTraceService;

    @InjectMocks private LocationKafkaConsumer consumer;

    @Test
    void givenRideCompletedEvent_whenOnRideCompleted_thenFlushTrace() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Instant startedAt = Instant.now().minusSeconds(600);
        Instant completedAt = Instant.now();

        RideCompletedEvent event = new RideCompletedEvent();
        event.setRideId(rideId);
        event.setDriverId(driverId);
        event.setCountryCode("TZ");
        event.setStartedAt(startedAt);
        event.setCompletedAt(completedAt);

        consumer.onRideCompleted(event);

        verify(tripTraceService).flushTrace(rideId, driverId, "TZ", startedAt, completedAt);
    }

    @Test
    void givenFlushTraceThrowsException_whenOnRideCompleted_thenExceptionCaught() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Instant startedAt = Instant.now().minusSeconds(600);
        Instant completedAt = Instant.now();

        RideCompletedEvent event = new RideCompletedEvent();
        event.setRideId(rideId);
        event.setDriverId(driverId);
        event.setCountryCode("TZ");
        event.setStartedAt(startedAt);
        event.setCompletedAt(completedAt);

        doThrow(new RuntimeException("Redis error"))
                .when(tripTraceService)
                .flushTrace(rideId, driverId, "TZ", startedAt, completedAt);

        // Should not throw - exception is caught internally
        consumer.onRideCompleted(event);
    }

    @Test
    void givenDriverGoesOffline_whenOnDriverStatusUpdated_thenNoError() {
        DriverStatusUpdatedEvent event = new DriverStatusUpdatedEvent();
        event.setDriverId(UUID.randomUUID());
        event.setPreviousStatus(DriverStatus.ONLINE_AVAILABLE);
        event.setNewStatus(DriverStatus.OFFLINE);

        // Should not throw - currently a no-op with logging
        consumer.onDriverStatusUpdated(event);
    }

    @Test
    void givenDriverGoesOnline_whenOnDriverStatusUpdated_thenNoError() {
        DriverStatusUpdatedEvent event = new DriverStatusUpdatedEvent();
        event.setDriverId(UUID.randomUUID());
        event.setPreviousStatus(DriverStatus.OFFLINE);
        event.setNewStatus(DriverStatus.ONLINE_AVAILABLE);

        consumer.onDriverStatusUpdated(event);
    }
}
