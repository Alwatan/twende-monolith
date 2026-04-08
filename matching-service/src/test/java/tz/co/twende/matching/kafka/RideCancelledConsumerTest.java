package tz.co.twende.matching.kafka;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import tz.co.twende.common.enums.RideStatus;
import tz.co.twende.common.event.ride.RideStatusUpdatedEvent;

@ExtendWith(MockitoExtension.class)
class RideCancelledConsumerTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private SetOperations<String, String> setOperations;

    @InjectMocks private RideCancelledConsumer consumer;

    @Test
    void givenCancelledRide_whenConsumed_thenRedisKeysCleanedUp() {
        UUID rideId = UUID.randomUUID();
        UUID driver1 = UUID.randomUUID();
        UUID driver2 = UUID.randomUUID();

        RideStatusUpdatedEvent event = new RideStatusUpdatedEvent();
        event.setRideId(rideId);
        event.setNewStatus(RideStatus.CANCELLED);

        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("rides_offered_to:" + rideId))
                .thenReturn(Set.of(driver1.toString(), driver2.toString()));

        consumer.onRideCancelled(event);

        verify(stringRedisTemplate).delete("ride_accepted:" + rideId);
        verify(stringRedisTemplate).delete("rides_offered_to:" + rideId);
        verify(stringRedisTemplate).delete("driver_rejected:" + rideId);
        verify(stringRedisTemplate).delete("ride_offer_batches:" + rideId);
        verify(stringRedisTemplate).delete("driver_offered:" + driver1 + ":" + rideId);
        verify(stringRedisTemplate).delete("driver_offered:" + driver2 + ":" + rideId);
    }

    @Test
    void givenCancelledRideWithNoOffers_whenConsumed_thenBasicKeysCleanedUp() {
        UUID rideId = UUID.randomUUID();

        RideStatusUpdatedEvent event = new RideStatusUpdatedEvent();
        event.setRideId(rideId);

        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("rides_offered_to:" + rideId)).thenReturn(null);

        consumer.onRideCancelled(event);

        verify(stringRedisTemplate).delete("ride_accepted:" + rideId);
        verify(stringRedisTemplate).delete("rides_offered_to:" + rideId);
        verify(stringRedisTemplate).delete("driver_rejected:" + rideId);
        verify(stringRedisTemplate).delete("ride_offer_batches:" + rideId);
    }
}
