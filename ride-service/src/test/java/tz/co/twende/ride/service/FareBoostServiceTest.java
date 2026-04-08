package tz.co.twende.ride.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.enums.RideStatus;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.ride.client.ConfigClient;
import tz.co.twende.ride.entity.Ride;
import tz.co.twende.ride.kafka.RideEventPublisher;
import tz.co.twende.ride.repository.RideRepository;

@ExtendWith(MockitoExtension.class)
class FareBoostServiceTest {

    @Mock private RideRepository rideRepository;
    @Mock private ConfigClient configClient;
    @Mock private RideEventPublisher eventPublisher;

    @InjectMocks private FareBoostService fareBoostService;

    @Test
    void givenRequestedRide_whenBoostFare_thenFareUpdatedAndEventPublished() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        Ride ride = createRide(rideId, riderId, RideStatus.REQUESTED, BigDecimal.valueOf(3500));

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(configClient.getMaxFareCap("TZ", "BAJAJ")).thenReturn(BigDecimal.valueOf(50000));
        when(rideRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Ride result = fareBoostService.boostFare(rideId, riderId, BigDecimal.valueOf(1000));

        assertEquals(BigDecimal.valueOf(4500), result.getEstimatedFare());
        assertEquals(BigDecimal.valueOf(1000), result.getFareBoostAmount());
        verify(eventPublisher)
                .publishFareBoosted(
                        any(), eq(BigDecimal.valueOf(3500)), eq(BigDecimal.valueOf(1000)));
    }

    @Test
    void givenNonRequestedRide_whenBoostFare_thenThrowBadRequest() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        Ride ride =
                createRide(rideId, riderId, RideStatus.DRIVER_ASSIGNED, BigDecimal.valueOf(3500));

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));

        assertThrows(
                BadRequestException.class,
                () -> fareBoostService.boostFare(rideId, riderId, BigDecimal.valueOf(1000)));
    }

    @Test
    void givenNegativeBoost_whenBoostFare_thenThrowBadRequest() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        Ride ride = createRide(rideId, riderId, RideStatus.REQUESTED, BigDecimal.valueOf(3500));

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));

        assertThrows(
                BadRequestException.class,
                () -> fareBoostService.boostFare(rideId, riderId, BigDecimal.valueOf(-100)));
    }

    @Test
    void givenBoostExceedsCap_whenBoostFare_thenThrowBadRequest() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        Ride ride = createRide(rideId, riderId, RideStatus.REQUESTED, BigDecimal.valueOf(3500));

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(configClient.getMaxFareCap("TZ", "BAJAJ")).thenReturn(BigDecimal.valueOf(4000));

        assertThrows(
                BadRequestException.class,
                () -> fareBoostService.boostFare(rideId, riderId, BigDecimal.valueOf(1000)));
    }

    @Test
    void givenDifferentRider_whenBoostFare_thenThrowUnauthorized() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        UUID otherRider = UUID.randomUUID();
        Ride ride = createRide(rideId, riderId, RideStatus.REQUESTED, BigDecimal.valueOf(3500));

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));

        assertThrows(
                UnauthorizedException.class,
                () -> fareBoostService.boostFare(rideId, otherRider, BigDecimal.valueOf(1000)));
    }

    @Test
    void givenMultipleBoosts_whenBoostFare_thenCumulativeBoostApplied() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        Ride ride = createRide(rideId, riderId, RideStatus.REQUESTED, BigDecimal.valueOf(3500));
        ride.setFareBoostAmount(BigDecimal.valueOf(500)); // already boosted once

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(configClient.getMaxFareCap("TZ", "BAJAJ")).thenReturn(BigDecimal.valueOf(50000));
        when(rideRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Ride result = fareBoostService.boostFare(rideId, riderId, BigDecimal.valueOf(1000));

        assertEquals(BigDecimal.valueOf(4500), result.getEstimatedFare());
        assertEquals(BigDecimal.valueOf(1500), result.getFareBoostAmount());
    }

    private Ride createRide(UUID rideId, UUID riderId, RideStatus status, BigDecimal fare) {
        Ride ride = new Ride();
        ride.setId(rideId);
        ride.setRiderId(riderId);
        ride.setStatus(status);
        ride.setCountryCode("TZ");
        ride.setVehicleType("BAJAJ");
        ride.setEstimatedFare(fare);
        ride.setFareBoostAmount(BigDecimal.ZERO);
        ride.setCurrencyCode("TZS");
        ride.setPickupLat(BigDecimal.valueOf(-6.77));
        ride.setPickupLng(BigDecimal.valueOf(39.23));
        ride.setPickupAddress("Test");
        ride.setDropoffLat(BigDecimal.valueOf(-6.81));
        ride.setDropoffLng(BigDecimal.valueOf(39.28));
        ride.setDropoffAddress("Test");
        ride.setRequestedAt(Instant.now());
        return ride;
    }
}
