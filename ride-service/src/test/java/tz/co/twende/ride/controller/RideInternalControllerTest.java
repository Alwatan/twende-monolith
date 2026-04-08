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
import tz.co.twende.ride.dto.request.DriverRejectedRequest;
import tz.co.twende.ride.dto.request.OfferAcceptedRequest;
import tz.co.twende.ride.dto.response.RideHistorySummaryDto;
import tz.co.twende.ride.dto.response.RideResponse;
import tz.co.twende.ride.entity.Ride;
import tz.co.twende.ride.mapper.RideMapper;
import tz.co.twende.ride.service.RideService;

@ExtendWith(MockitoExtension.class)
class RideInternalControllerTest {

    @Mock private RideService rideService;
    @Mock private RideMapper rideMapper;

    @InjectMocks private RideInternalController controller;

    @Test
    void givenOfferAccepted_whenOfferAccepted_thenReturn200() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        OfferAcceptedRequest request = new OfferAcceptedRequest(driverId, 120);
        Ride ride = createTestRide();
        RideResponse response = new RideResponse();

        when(rideService.assignDriver(rideId, driverId, 120)).thenReturn(ride);
        when(rideMapper.toResponse(ride)).thenReturn(response);

        ResponseEntity<ApiResponse<RideResponse>> result =
                controller.offerAccepted(rideId, request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void givenDriverRejected_whenDriverRejected_thenReturn200() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        DriverRejectedRequest request = new DriverRejectedRequest(driverId);

        ResponseEntity<ApiResponse<Void>> result = controller.driverRejected(rideId, request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(rideService).handleDriverRejection(rideId, driverId);
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
    void givenHistoryParams_whenGetRideHistory_thenReturn200() {
        UUID userId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();
        Ride ride = createTestRide();
        Page<Ride> page = new PageImpl<>(List.of(ride));
        RideHistorySummaryDto dto = new RideHistorySummaryDto();

        when(rideService.getRideHistoryByCity(userId, cityId, 5)).thenReturn(page);
        when(rideMapper.toHistorySummaryList(any())).thenReturn(List.of(dto));

        ResponseEntity<ApiResponse<List<RideHistorySummaryDto>>> result =
                controller.getRideHistory(userId, cityId, 5);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertTrue(result.getBody().isSuccess());
        assertEquals(1, result.getBody().getData().size());
    }

    @Test
    void givenCustomLimit_whenGetRideHistory_thenUsesProvidedLimit() {
        UUID userId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();
        Page<Ride> page = new PageImpl<>(List.of());

        when(rideService.getRideHistoryByCity(userId, cityId, 10)).thenReturn(page);
        when(rideMapper.toHistorySummaryList(any())).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<RideHistorySummaryDto>>> result =
                controller.getRideHistory(userId, cityId, 10);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(rideService).getRideHistoryByCity(userId, cityId, 10);
    }

    @Test
    void givenNoCompletedRides_whenGetRideHistory_thenReturnEmptyList() {
        UUID userId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();
        Page<Ride> emptyPage = new PageImpl<>(List.of());

        when(rideService.getRideHistoryByCity(userId, cityId, 5)).thenReturn(emptyPage);
        when(rideMapper.toHistorySummaryList(any())).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<RideHistorySummaryDto>>> result =
                controller.getRideHistory(userId, cityId, 5);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertTrue(result.getBody().getData().isEmpty());
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
