package tz.co.twende.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import tz.co.twende.location.dto.NearbyDriverResponse;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private GeoOperations<String, Object> geoOperations;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private ListOperations<String, Object> listOperations;

    @InjectMocks private LocationService locationService;

    @Test
    void givenDriverLocation_whenUpdateDriverLocation_thenGeoAddAndHashSetCalled() {
        UUID driverId = UUID.randomUUID();
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        locationService.updateDriverLocation(
                driverId,
                "TZ",
                "BAJAJ",
                new BigDecimal("-6.7924"),
                new BigDecimal("39.2083"),
                45,
                30);

        verify(geoOperations).add(eq("drivers:TZ:BAJAJ"), any(), eq(driverId.toString()));
        verify(hashOperations).putAll(eq("driver:location:" + driverId), any(Map.class));
        verify(redisTemplate).expire(eq("driver:location:" + driverId), anyLong(), any());
    }

    @Test
    void givenDriver_whenRemoveFromGeoIndex_thenGeoRemoveAndDeleteCalled() {
        UUID driverId = UUID.randomUUID();
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);

        locationService.removeDriverFromGeoIndex(driverId, "TZ", "BAJAJ");

        verify(geoOperations).remove("drivers:TZ:BAJAJ", driverId.toString());
        verify(redisTemplate).delete("driver:location:" + driverId);
    }

    @Test
    void givenDriverExists_whenGetDriverLocation_thenReturnDetails() {
        UUID driverId = UUID.randomUUID();
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("driver:location:" + driverId))
                .thenReturn(
                        Map.of(
                                "lat", "-6.7924",
                                "lng", "39.2083",
                                "bearing", "45",
                                "speed", "30"));

        NearbyDriverResponse result = locationService.getDriverLocation(driverId);

        assertThat(result).isNotNull();
        assertThat(result.getDriverId()).isEqualTo(driverId);
        assertThat(result.getLatitude()).isEqualByComparingTo(new BigDecimal("-6.7924"));
        assertThat(result.getBearing()).isEqualTo(45);
    }

    @Test
    void givenNoDriver_whenGetDriverLocation_thenReturnNull() {
        UUID driverId = UUID.randomUUID();
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("driver:location:" + driverId)).thenReturn(Map.of());

        NearbyDriverResponse result = locationService.getDriverLocation(driverId);

        assertThat(result).isNull();
    }

    @Test
    void givenActiveRide_whenAppendToTripTrace_thenRedisPushCalled() {
        UUID rideId = UUID.randomUUID();
        when(redisTemplate.opsForList()).thenReturn(listOperations);

        locationService.appendToTripTrace(
                rideId, new BigDecimal("-6.7924"), new BigDecimal("39.2083"));

        verify(listOperations).rightPush(eq("ride:trace:" + rideId), anyString());
        verify(redisTemplate).expire(eq("ride:trace:" + rideId), anyLong(), any());
    }
}
