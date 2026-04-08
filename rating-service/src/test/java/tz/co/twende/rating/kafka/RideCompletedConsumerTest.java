package tz.co.twende.rating.kafka;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.rating.dto.RideDetailsDto;

class RideCompletedConsumerTest {

    private final RideCompletedConsumer consumer = new RideCompletedConsumer();

    @Test
    void givenEvent_whenConsumed_thenRideCached() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        RideCompletedEvent event = new RideCompletedEvent();
        event.setRideId(rideId);
        event.setRiderId(riderId);
        event.setDriverId(driverId);
        event.setCountryCode("TZ");
        event.setCompletedAt(Instant.now());
        event.setFinalFare(BigDecimal.valueOf(5000));

        consumer.onRideCompleted(event);

        RideDetailsDto cached = consumer.getCachedRide(rideId);
        assertThat(cached).isNotNull();
        assertThat(cached.getRiderId()).isEqualTo(riderId);
        assertThat(cached.getDriverId()).isEqualTo(driverId);
        assertThat(cached.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void givenCachedRide_whenEvicted_thenReturnsNull() {
        UUID rideId = UUID.randomUUID();

        RideCompletedEvent event = new RideCompletedEvent();
        event.setRideId(rideId);
        event.setRiderId(UUID.randomUUID());
        event.setDriverId(UUID.randomUUID());
        event.setCountryCode("TZ");
        event.setCompletedAt(Instant.now());

        consumer.onRideCompleted(event);
        assertThat(consumer.getCachedRide(rideId)).isNotNull();

        consumer.evict(rideId);
        assertThat(consumer.getCachedRide(rideId)).isNull();
    }

    @Test
    void givenNoCachedRide_whenGet_thenReturnsNull() {
        assertThat(consumer.getCachedRide(UUID.randomUUID())).isNull();
    }
}
