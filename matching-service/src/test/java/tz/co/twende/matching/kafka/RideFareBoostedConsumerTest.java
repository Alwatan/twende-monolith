package tz.co.twende.matching.kafka;

import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tz.co.twende.common.event.ride.RideFareBoostedEvent;
import tz.co.twende.matching.client.LocationServiceClient;
import tz.co.twende.matching.service.BroadcastService;
import tz.co.twende.matching.service.DriverScoringService;
import tz.co.twende.matching.service.MatchingService;

@ExtendWith(MockitoExtension.class)
class RideFareBoostedConsumerTest {

    @Mock private LocationServiceClient locationServiceClient;
    @Mock private MatchingService matchingService;
    @Mock private DriverScoringService driverScoringService;
    @Mock private BroadcastService broadcastService;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks private RideFareBoostedConsumer consumer;

    @Test
    void givenFareBoostedEvent_whenConsumed_thenProcessedWithoutError() {
        UUID rideId = UUID.randomUUID();

        RideFareBoostedEvent event = new RideFareBoostedEvent();
        event.setRideId(rideId);
        event.setPreviousFare(new BigDecimal("5000"));
        event.setNewFare(new BigDecimal("7000"));
        event.setBoostAmount(new BigDecimal("2000"));

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ride_offer_batches:" + rideId)).thenReturn("1");

        consumer.onFareBoosted(event);

        // Verify no exception thrown and batch key was read
        verify(valueOperations).get("ride_offer_batches:" + rideId);
    }

    @Test
    void givenFareBoostedEventWithNoBatchKey_whenConsumed_thenDefaultsToOne() {
        UUID rideId = UUID.randomUUID();

        RideFareBoostedEvent event = new RideFareBoostedEvent();
        event.setRideId(rideId);
        event.setPreviousFare(new BigDecimal("3000"));
        event.setNewFare(new BigDecimal("5000"));
        event.setBoostAmount(new BigDecimal("2000"));

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ride_offer_batches:" + rideId)).thenReturn(null);

        consumer.onFareBoosted(event);

        verify(valueOperations).get("ride_offer_batches:" + rideId);
    }
}
