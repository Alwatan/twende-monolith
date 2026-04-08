package tz.co.twende.ride.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import tz.co.twende.common.enums.RideStatus;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.ride.client.LocationClient;
import tz.co.twende.ride.client.LoyaltyClient;
import tz.co.twende.ride.client.PricingClient;
import tz.co.twende.ride.dto.request.CreateRideRequest;
import tz.co.twende.ride.dto.response.EstimateDto;
import tz.co.twende.ride.entity.Ride;
import tz.co.twende.ride.entity.RideDriverRejection;
import tz.co.twende.ride.kafka.RideEventPublisher;
import tz.co.twende.ride.repository.RideDriverRejectionRepository;
import tz.co.twende.ride.repository.RideRepository;
import tz.co.twende.ride.repository.RideStatusEventRepository;

@ExtendWith(MockitoExtension.class)
class RideServiceTest {

    @Mock private RideRepository rideRepository;
    @Mock private RideStatusEventRepository statusEventRepository;
    @Mock private RideDriverRejectionRepository rejectionRepository;
    @Mock private PricingClient pricingClient;
    @Mock private LocationClient locationClient;
    @Mock private LoyaltyClient loyaltyClient;
    @Mock private RideEventPublisher eventPublisher;
    @Mock private TripOtpService tripOtpService;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private RideService rideService;

    @Test
    void givenValidRequest_whenCreateRide_thenRideCreatedWithRequestedStatus() {
        UUID riderId = UUID.randomUUID();
        CreateRideRequest request = new CreateRideRequest();
        request.setVehicleType("BAJAJ");
        request.setPickupLat(BigDecimal.valueOf(-6.7728));
        request.setPickupLng(BigDecimal.valueOf(39.2310));
        request.setPickupAddress("Kariakoo Market");
        request.setDropoffLat(BigDecimal.valueOf(-6.8160));
        request.setDropoffLng(BigDecimal.valueOf(39.2803));
        request.setDropoffAddress("Mlimani City");

        when(locationClient.isInRestrictedZone(any(), any())).thenReturn(false);
        EstimateDto estimate = new EstimateDto();
        estimate.setEstimatedFare(BigDecimal.valueOf(3500));
        estimate.setCurrency("TZS");
        estimate.setDistanceMetres(5000);
        estimate.setDurationSeconds(600);
        when(pricingClient.getFareEstimate(any(), any(), any(), any(), any(), any()))
                .thenReturn(estimate);
        when(loyaltyClient.findApplicableOffer(any(), any(), any())).thenReturn(null);
        when(rideRepository.save(any(Ride.class)))
                .thenAnswer(
                        invocation -> {
                            Ride r = invocation.getArgument(0);
                            if (r.getId() == null) {
                                r.setId(UUID.randomUUID());
                            }
                            return r;
                        });
        when(statusEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Ride ride = rideService.createRide(riderId, "TZ", request);

        assertNotNull(ride);
        assertEquals(RideStatus.REQUESTED, ride.getStatus());
        assertEquals(riderId, ride.getRiderId());
        assertEquals("BAJAJ", ride.getVehicleType());
        assertEquals(BigDecimal.valueOf(3500), ride.getEstimatedFare());
        assertNotNull(ride.getMatchingTimeoutAt());
        verify(eventPublisher).publishRideRequested(any());
    }

    @Test
    void givenRestrictedZone_whenCreateRide_thenThrowBadRequest() {
        UUID riderId = UUID.randomUUID();
        CreateRideRequest request = new CreateRideRequest();
        request.setVehicleType("BAJAJ");
        request.setPickupLat(BigDecimal.valueOf(-6.7728));
        request.setPickupLng(BigDecimal.valueOf(39.2310));
        request.setPickupAddress("Restricted Area");
        request.setDropoffLat(BigDecimal.valueOf(-6.8160));
        request.setDropoffLng(BigDecimal.valueOf(39.2803));
        request.setDropoffAddress("Dest");

        when(locationClient.isInRestrictedZone(any(), any())).thenReturn(true);

        assertThrows(
                BadRequestException.class, () -> rideService.createRide(riderId, "TZ", request));
    }

    @Test
    void givenRequestedRide_whenAssignDriver_thenStatusChangesToDriverAssigned() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Ride ride = createRideWithStatus(rideId, RideStatus.REQUESTED);

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(rideRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(statusEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Ride result = rideService.assignDriver(rideId, driverId, 120);

        assertEquals(RideStatus.DRIVER_ASSIGNED, result.getStatus());
        assertEquals(driverId, result.getDriverId());
        assertNotNull(result.getAssignedAt());
        verify(eventPublisher)
                .publishStatusUpdated(
                        any(), eq(RideStatus.REQUESTED), eq(RideStatus.DRIVER_ASSIGNED));
    }

    @Test
    void givenAlreadyAssignedToSameDriver_whenAssignDriver_thenNoOp() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Ride ride = createRideWithStatus(rideId, RideStatus.DRIVER_ASSIGNED);
        ride.setDriverId(driverId);

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));

        Ride result = rideService.assignDriver(rideId, driverId, 120);

        assertEquals(RideStatus.DRIVER_ASSIGNED, result.getStatus());
        verify(rideRepository, never()).save(any());
    }

    @Test
    void givenInProgressRide_whenAssignDriver_thenThrowBadRequest() {
        UUID rideId = UUID.randomUUID();
        Ride ride = createRideWithStatus(rideId, RideStatus.IN_PROGRESS);

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));

        assertThrows(
                BadRequestException.class,
                () -> rideService.assignDriver(rideId, UUID.randomUUID(), 120));
    }

    @Test
    void givenAssignedRide_whenDriverArrived_thenStatusChangesAndOtpGenerated() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Ride ride = createRideWithStatus(rideId, RideStatus.DRIVER_ASSIGNED);
        ride.setDriverId(driverId);

        when(rideRepository.findByIdAndDriverId(rideId, driverId)).thenReturn(Optional.of(ride));
        when(rideRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(statusEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Ride result = rideService.driverArrived(rideId, driverId);

        assertEquals(RideStatus.DRIVER_ARRIVED, result.getStatus());
        assertNotNull(result.getArrivedAt());
        verify(tripOtpService).generateAndSendOtp(any());
    }

    @Test
    void givenArrivedRide_whenStartTripWithValidOtp_thenStatusInProgress() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Ride ride = createRideWithStatus(rideId, RideStatus.DRIVER_ARRIVED);
        ride.setDriverId(driverId);
        ride.setTripStartOtpHash("$2a$04$hash");
        ride.setTripStartOtpExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));

        when(rideRepository.findByIdAndDriverId(rideId, driverId)).thenReturn(Optional.of(ride));
        when(tripOtpService.verifyOtp(ride, "1234")).thenReturn(true);
        when(rideRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(statusEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Ride result = rideService.startTrip(rideId, driverId, "1234");

        assertEquals(RideStatus.IN_PROGRESS, result.getStatus());
        assertNotNull(result.getStartedAt());
    }

    @Test
    void givenRequestedRide_whenStartTrip_thenThrowBadRequest() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Ride ride = createRideWithStatus(rideId, RideStatus.REQUESTED);
        ride.setDriverId(driverId);

        when(rideRepository.findByIdAndDriverId(rideId, driverId)).thenReturn(Optional.of(ride));

        assertThrows(
                BadRequestException.class, () -> rideService.startTrip(rideId, driverId, "1234"));
    }

    @Test
    void givenInProgressRide_whenCompleteTrip_thenStatusCompletedAndFareSet() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Ride ride = createRideWithStatus(rideId, RideStatus.IN_PROGRESS);
        ride.setDriverId(driverId);
        ride.setEstimatedFare(BigDecimal.valueOf(3500));
        ride.setDistanceMetres(5000);
        ride.setDurationSeconds(600);

        EstimateDto finalCalc = new EstimateDto();
        finalCalc.setEstimatedFare(BigDecimal.valueOf(3800));

        when(rideRepository.findByIdAndDriverId(rideId, driverId)).thenReturn(Optional.of(ride));
        when(pricingClient.calculateFinalFare(any(), any(), any(), any())).thenReturn(finalCalc);
        when(rideRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(statusEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Ride result = rideService.completeTrip(rideId, driverId);

        assertEquals(RideStatus.COMPLETED, result.getStatus());
        assertEquals(BigDecimal.valueOf(3800), result.getFinalFare());
        assertNotNull(result.getCompletedAt());
        verify(eventPublisher).publishRideCompleted(any());
    }

    @Test
    void givenRequestedRide_whenCancelByRider_thenStatusCancelled() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        Ride ride = createRideWithStatus(rideId, RideStatus.REQUESTED);
        ride.setRiderId(riderId);

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(rideRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(statusEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Ride result = rideService.cancelRide(rideId, riderId, "RIDER", "Changed my mind");

        assertEquals(RideStatus.CANCELLED, result.getStatus());
        assertEquals("RIDER", result.getCancelledBy());
        assertEquals("Changed my mind", result.getCancelReason());
        verify(eventPublisher).publishRideCancelled(any());
    }

    @Test
    void givenCompletedRide_whenCancel_thenThrowBadRequest() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        Ride ride = createRideWithStatus(rideId, RideStatus.COMPLETED);
        ride.setRiderId(riderId);

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));

        assertThrows(
                BadRequestException.class,
                () -> rideService.cancelRide(rideId, riderId, "RIDER", "reason"));
    }

    @Test
    void givenRide_whenCancelByDifferentRider_thenThrowUnauthorized() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        UUID otherRider = UUID.randomUUID();
        Ride ride = createRideWithStatus(rideId, RideStatus.REQUESTED);
        ride.setRiderId(riderId);

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));

        assertThrows(
                UnauthorizedException.class,
                () -> rideService.cancelRide(rideId, otherRider, "RIDER", "reason"));
    }

    @Test
    void givenFirstRejection_whenHandleDriverRejection_thenCountIncrementedAndNotificationSent() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        Ride ride = createRideWithStatus(rideId, RideStatus.REQUESTED);
        ride.setRiderId(riderId);
        ride.setDriverRejectionCount(0);

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(rejectionRepository.existsByRideIdAndDriverId(rideId, driverId)).thenReturn(false);
        when(rejectionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(rideRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        rideService.handleDriverRejection(rideId, driverId);

        assertEquals(1, ride.getDriverRejectionCount());
        verify(kafkaTemplate).send(eq("twende.notifications.send"), any());
    }

    @Test
    void givenThreeRejections_whenHandleDriverRejection_thenNudgeSent() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        Ride ride = createRideWithStatus(rideId, RideStatus.REQUESTED);
        ride.setRiderId(riderId);
        ride.setDriverRejectionCount(2); // will become 3

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(rejectionRepository.existsByRideIdAndDriverId(rideId, driverId)).thenReturn(false);
        when(rejectionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(rideRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        rideService.handleDriverRejection(rideId, driverId);

        assertEquals(3, ride.getDriverRejectionCount());
        // Two notifications: count update + nudge
        verify(kafkaTemplate, times(2)).send(eq("twende.notifications.send"), any());
    }

    @Test
    void givenDuplicateRejection_whenHandleDriverRejection_thenNoOp() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Ride ride = createRideWithStatus(rideId, RideStatus.REQUESTED);

        when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));
        when(rejectionRepository.existsByRideIdAndDriverId(rideId, driverId)).thenReturn(true);

        rideService.handleDriverRejection(rideId, driverId);

        verify(rejectionRepository, never()).save(any(RideDriverRejection.class));
        verify(rideRepository, never()).save(any());
    }

    @Test
    void givenNonExistentRide_whenGetRide_thenThrowNotFound() {
        UUID rideId = UUID.randomUUID();
        when(rideRepository.findById(rideId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> rideService.getRide(rideId));
    }

    @Test
    void givenCompletedRides_whenGetRideHistoryByCity_thenReturnPagedResults() {
        UUID riderId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();
        Ride ride1 = createRideWithStatus(UUID.randomUUID(), RideStatus.COMPLETED);
        ride1.setRiderId(riderId);
        ride1.setCityId(cityId);
        ride1.setCompletedAt(Instant.now());
        ride1.setFinalFare(BigDecimal.valueOf(3500));

        Ride ride2 = createRideWithStatus(UUID.randomUUID(), RideStatus.COMPLETED);
        ride2.setRiderId(riderId);
        ride2.setCityId(cityId);
        ride2.setCompletedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        ride2.setFinalFare(BigDecimal.valueOf(5000));

        Page<Ride> page = new PageImpl<>(List.of(ride1, ride2));
        when(rideRepository.findByRiderIdAndCityIdAndStatusOrderByCompletedAtDesc(
                        riderId, cityId, RideStatus.COMPLETED, PageRequest.of(0, 5)))
                .thenReturn(page);

        Page<Ride> result = rideService.getRideHistoryByCity(riderId, cityId, 5);

        assertEquals(2, result.getContent().size());
        assertEquals(ride1.getId(), result.getContent().get(0).getId());
        assertEquals(ride2.getId(), result.getContent().get(1).getId());
    }

    @Test
    void givenNoCompletedRides_whenGetRideHistoryByCity_thenReturnEmptyPage() {
        UUID riderId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();

        Page<Ride> emptyPage = new PageImpl<>(List.of());
        when(rideRepository.findByRiderIdAndCityIdAndStatusOrderByCompletedAtDesc(
                        riderId, cityId, RideStatus.COMPLETED, PageRequest.of(0, 5)))
                .thenReturn(emptyPage);

        Page<Ride> result = rideService.getRideHistoryByCity(riderId, cityId, 5);

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void givenLimitExceedingMax_whenGetRideHistoryByCity_thenCapAtTen() {
        UUID riderId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();

        Page<Ride> emptyPage = new PageImpl<>(List.of());
        when(rideRepository.findByRiderIdAndCityIdAndStatusOrderByCompletedAtDesc(
                        riderId, cityId, RideStatus.COMPLETED, PageRequest.of(0, 10)))
                .thenReturn(emptyPage);

        Page<Ride> result = rideService.getRideHistoryByCity(riderId, cityId, 50);

        assertNotNull(result);
        // Verify the repository was called with limit capped at 10
        verify(rideRepository)
                .findByRiderIdAndCityIdAndStatusOrderByCompletedAtDesc(
                        riderId, cityId, RideStatus.COMPLETED, PageRequest.of(0, 10));
    }

    private Ride createRideWithStatus(UUID rideId, RideStatus status) {
        Ride ride = new Ride();
        ride.setId(rideId);
        ride.setStatus(status);
        ride.setCountryCode("TZ");
        ride.setRiderId(UUID.randomUUID());
        ride.setVehicleType("BAJAJ");
        ride.setPickupLat(BigDecimal.valueOf(-6.7728));
        ride.setPickupLng(BigDecimal.valueOf(39.2310));
        ride.setPickupAddress("Test Pickup");
        ride.setDropoffLat(BigDecimal.valueOf(-6.8160));
        ride.setDropoffLng(BigDecimal.valueOf(39.2803));
        ride.setDropoffAddress("Test Dropoff");
        ride.setEstimatedFare(BigDecimal.valueOf(3500));
        ride.setFareBoostAmount(BigDecimal.ZERO);
        ride.setCurrencyCode("TZS");
        ride.setRequestedAt(Instant.now());
        return ride;
    }
}
