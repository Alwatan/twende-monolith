package tz.co.twende.ride.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tz.co.twende.common.enums.RideStatus;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.ride.dto.request.BoostFareRequest;
import tz.co.twende.ride.dto.request.CancelRideRequest;
import tz.co.twende.ride.dto.request.CreateRideRequest;
import tz.co.twende.ride.dto.request.StartTripRequest;
import tz.co.twende.ride.dto.response.RideResponse;
import tz.co.twende.ride.entity.Ride;
import tz.co.twende.ride.mapper.RideMapper;
import tz.co.twende.ride.service.FareBoostService;
import tz.co.twende.ride.service.RideService;

@ExtendWith(MockitoExtension.class)
class RideControllerTest {

    @Mock private RideService rideService;
    @Mock private FareBoostService fareBoostService;
    @Mock private RideMapper rideMapper;

    @InjectMocks private RideController controller;

    @Test
    void givenValidRequest_whenCreateRide_thenReturn201() {
        UUID userId = UUID.randomUUID();
        CreateRideRequest request = new CreateRideRequest();
        Ride ride = createTestRide();
        RideResponse response = new RideResponse();
        response.setStatus("REQUESTED");

        when(rideService.createRide(eq(userId), eq("TZ"), any())).thenReturn(ride);
        when(rideMapper.toResponse(ride)).thenReturn(response);

        ResponseEntity<ApiResponse<RideResponse>> result =
                controller.createRide(userId, "TZ", request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertTrue(result.getBody().isSuccess());
    }

    @Test
    void givenRideId_whenGetRide_thenReturn200() {
        UUID rideId = UUID.randomUUID();
        Ride ride = createTestRide();
        RideResponse response = new RideResponse();

        when(rideService.getRide(rideId)).thenReturn(ride);
        when(rideMapper.toResponse(ride)).thenReturn(response);

        ResponseEntity<ApiResponse<RideResponse>> result = controller.getRide(rideId);

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void givenBoostRequest_whenBoostFare_thenReturn200() {
        UUID rideId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BoostFareRequest request = new BoostFareRequest(BigDecimal.valueOf(1000));
        Ride ride = createTestRide();
        RideResponse response = new RideResponse();

        when(fareBoostService.boostFare(rideId, userId, BigDecimal.valueOf(1000))).thenReturn(ride);
        when(rideMapper.toResponse(ride)).thenReturn(response);

        ResponseEntity<ApiResponse<RideResponse>> result =
                controller.boostFare(rideId, userId, request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void givenCancelRequest_whenCancelRide_thenReturn200() {
        UUID rideId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CancelRideRequest request = new CancelRideRequest("reason");
        Ride ride = createTestRide();
        RideResponse response = new RideResponse();

        when(rideService.cancelRide(rideId, userId, "RIDER", "reason")).thenReturn(ride);
        when(rideMapper.toResponse(ride)).thenReturn(response);

        ResponseEntity<ApiResponse<RideResponse>> result =
                controller.cancelRide(rideId, userId, "RIDER", request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void givenNullCancelRequest_whenCancelRide_thenReturn200() {
        UUID rideId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Ride ride = createTestRide();
        RideResponse response = new RideResponse();

        when(rideService.cancelRide(rideId, userId, "RIDER", null)).thenReturn(ride);
        when(rideMapper.toResponse(ride)).thenReturn(response);

        ResponseEntity<ApiResponse<RideResponse>> result =
                controller.cancelRide(rideId, userId, "RIDER", null);

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void givenDriverArrived_whenMarkArrived_thenReturn200() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Ride ride = createTestRide();
        RideResponse response = new RideResponse();

        when(rideService.driverArrived(rideId, driverId)).thenReturn(ride);
        when(rideMapper.toResponse(ride)).thenReturn(response);

        ResponseEntity<ApiResponse<RideResponse>> result =
                controller.driverArrived(rideId, driverId);

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void givenStartTrip_whenStartTrip_thenReturn200() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        StartTripRequest request = new StartTripRequest("1234");
        Ride ride = createTestRide();
        RideResponse response = new RideResponse();

        when(rideService.startTrip(rideId, driverId, "1234")).thenReturn(ride);
        when(rideMapper.toResponse(ride)).thenReturn(response);

        ResponseEntity<ApiResponse<RideResponse>> result =
                controller.startTrip(rideId, driverId, request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void givenComplete_whenCompleteTrip_thenReturn200() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Ride ride = createTestRide();
        RideResponse response = new RideResponse();

        when(rideService.completeTrip(rideId, driverId)).thenReturn(ride);
        when(rideMapper.toResponse(ride)).thenReturn(response);

        ResponseEntity<ApiResponse<RideResponse>> result =
                controller.completeTrip(rideId, driverId);

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void givenResendOtp_whenResend_thenReturn200() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        Ride ride = createTestRide();
        RideResponse response = new RideResponse();

        when(rideService.resendOtp(rideId, riderId)).thenReturn(ride);
        when(rideMapper.toResponse(ride)).thenReturn(response);

        ResponseEntity<ApiResponse<RideResponse>> result = controller.resendOtp(rideId, riderId);

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void givenDriverRole_whenGetActiveRides_thenReturnDriverRides() {
        UUID userId = UUID.randomUUID();
        Ride ride = createTestRide();
        RideResponse response = new RideResponse();

        when(rideService.getActiveRidesForDriver(userId)).thenReturn(List.of(ride));
        when(rideMapper.toResponseList(any())).thenReturn(List.of(response));

        ResponseEntity<ApiResponse<List<RideResponse>>> result =
                controller.getActiveRides(userId, "DRIVER");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(rideService).getActiveRidesForDriver(userId);
    }

    @Test
    void givenRiderRole_whenGetActiveRides_thenReturnRiderRides() {
        UUID userId = UUID.randomUUID();
        Ride ride = createTestRide();
        RideResponse response = new RideResponse();

        when(rideService.getActiveRidesForRider(userId)).thenReturn(List.of(ride));
        when(rideMapper.toResponseList(any())).thenReturn(List.of(response));

        ResponseEntity<ApiResponse<List<RideResponse>>> result =
                controller.getActiveRides(userId, "RIDER");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(rideService).getActiveRidesForRider(userId);
    }

    @Test
    void givenRideHistory_whenGetHistory_thenReturnPaginated() {
        UUID userId = UUID.randomUUID();
        Ride ride = createTestRide();
        RideResponse response = new RideResponse();
        Page<Ride> page = new PageImpl<>(List.of(ride));

        when(rideService.getRideHistory(eq(userId), any())).thenReturn(page);
        when(rideMapper.toResponse(ride)).thenReturn(response);

        ResponseEntity<ApiResponse<Page<RideResponse>>> result =
                controller.getRideHistory(userId, 0, 20);

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    private Ride createTestRide() {
        Ride ride = new Ride();
        ride.setId(UUID.randomUUID());
        ride.setRiderId(UUID.randomUUID());
        ride.setCountryCode("TZ");
        ride.setVehicleType("BAJAJ");
        ride.setStatus(RideStatus.REQUESTED);
        ride.setEstimatedFare(BigDecimal.valueOf(3500));
        ride.setFareBoostAmount(BigDecimal.ZERO);
        ride.setCurrencyCode("TZS");
        ride.setRequestedAt(Instant.now());
        return ride;
    }
}
