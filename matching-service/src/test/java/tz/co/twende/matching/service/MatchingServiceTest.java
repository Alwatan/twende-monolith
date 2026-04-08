package tz.co.twende.matching.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tz.co.twende.common.enums.VehicleType;
import tz.co.twende.common.event.Location;
import tz.co.twende.common.event.ride.RideRequestedEvent;
import tz.co.twende.matching.client.LocationServiceClient;
import tz.co.twende.matching.dto.DriverCandidate;
import tz.co.twende.matching.dto.NearbyDriverDto;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock private LocationServiceClient locationServiceClient;
    @Mock private DriverScoringService driverScoringService;
    @Mock private BroadcastService broadcastService;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private SetOperations<String, String> setOperations;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks private MatchingService matchingService;

    @Test
    void givenNearbyDrivers_whenRideRequested_thenBroadcastsCalled() {
        RideRequestedEvent event = buildRideRequestedEvent();
        UUID driverId = UUID.randomUUID();

        NearbyDriverDto nearbyDriver =
                NearbyDriverDto.builder()
                        .driverId(driverId)
                        .distanceKm(new BigDecimal("1.0"))
                        .build();

        DriverCandidate candidate =
                DriverCandidate.builder()
                        .driverId(driverId)
                        .distanceKm(new BigDecimal("1.0"))
                        .compositeScore(new BigDecimal("0.8"))
                        .build();

        when(locationServiceClient.findNearbyDrivers(eq("TZ"), eq("BAJAJ"), any(), any(), eq(3.0)))
                .thenReturn(List.of(nearbyDriver));

        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(anyString())).thenReturn(Collections.emptySet());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        when(driverScoringService.scoreAndRank(anyList(), eq(3.0))).thenReturn(List.of(candidate));

        matchingService.onRideRequested(event);

        verify(broadcastService)
                .broadcastOffers(
                        eq(event.getRideId()),
                        eq(event.getRiderId()),
                        eq("TZ"),
                        anyList(),
                        eq(1),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyInt());
    }

    @Test
    void givenNoNearbyDrivers_whenRideRequested_thenNoBroadcast() {
        RideRequestedEvent event = buildRideRequestedEvent();

        when(locationServiceClient.findNearbyDrivers(eq("TZ"), eq("BAJAJ"), any(), any(), eq(3.0)))
                .thenReturn(Collections.emptyList());

        matchingService.onRideRequested(event);

        verify(broadcastService, never())
                .broadcastOffers(
                        any(), any(), any(), anyList(), anyInt(), any(), any(), any(), any(), any(),
                        anyInt());
    }

    @Test
    void givenOfferedAndRejectedDrivers_whenFilterCandidates_thenExcluded() {
        UUID rideId = UUID.randomUUID();
        UUID offeredDriver = UUID.randomUUID();
        UUID rejectedDriver = UUID.randomUUID();
        UUID newDriver = UUID.randomUUID();

        NearbyDriverDto d1 =
                NearbyDriverDto.builder()
                        .driverId(offeredDriver)
                        .distanceKm(new BigDecimal("1.0"))
                        .build();
        NearbyDriverDto d2 =
                NearbyDriverDto.builder()
                        .driverId(rejectedDriver)
                        .distanceKm(new BigDecimal("1.5"))
                        .build();
        NearbyDriverDto d3 =
                NearbyDriverDto.builder()
                        .driverId(newDriver)
                        .distanceKm(new BigDecimal("2.0"))
                        .build();

        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("rides_offered_to:" + rideId))
                .thenReturn(Set.of(offeredDriver.toString()));
        when(setOperations.members("driver_rejected:" + rideId))
                .thenReturn(Set.of(rejectedDriver.toString()));

        List<NearbyDriverDto> result =
                matchingService.filterCandidates(rideId, List.of(d1, d2, d3));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDriverId()).isEqualTo(newDriver);
    }

    private RideRequestedEvent buildRideRequestedEvent() {
        RideRequestedEvent event = new RideRequestedEvent();
        event.setRideId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());
        event.setCountryCode("TZ");
        event.setVehicleType(VehicleType.BAJAJ);
        event.setPickupLocation(
                new Location(new BigDecimal("-6.8"), new BigDecimal("39.3"), "Test Location"));
        event.setDropoffLocation(
                new Location(new BigDecimal("-6.82"), new BigDecimal("39.28"), "Destination"));
        event.setEstimatedFare(new BigDecimal("5000"));
        return event;
    }
}
