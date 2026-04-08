package tz.co.twende.ride.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.ride.dto.request.DriverRejectedRequest;
import tz.co.twende.ride.dto.request.OfferAcceptedRequest;
import tz.co.twende.ride.dto.response.RideHistorySummaryDto;
import tz.co.twende.ride.dto.response.RideResponse;
import tz.co.twende.ride.entity.Ride;
import tz.co.twende.ride.mapper.RideMapper;
import tz.co.twende.ride.service.RideService;

@RestController
@RequestMapping("/internal/rides")
@RequiredArgsConstructor
public class RideInternalController {

    private final RideService rideService;
    private final RideMapper rideMapper;

    /** Called by matching-service when a driver accepts a ride offer. */
    @PutMapping("/{rideId}/offer-accepted")
    public ResponseEntity<ApiResponse<RideResponse>> offerAccepted(
            @PathVariable UUID rideId, @Valid @RequestBody OfferAcceptedRequest request) {
        Ride ride =
                rideService.assignDriver(
                        rideId, request.getDriverId(), request.getEstimatedArrivalSeconds());
        return ResponseEntity.ok(ApiResponse.ok(rideMapper.toResponse(ride)));
    }

    /** Called by matching-service when a driver rejects a ride offer. */
    @PutMapping("/{rideId}/driver-rejected")
    public ResponseEntity<ApiResponse<Void>> driverRejected(
            @PathVariable UUID rideId, @Valid @RequestBody DriverRejectedRequest request) {
        rideService.handleDriverRejection(rideId, request.getDriverId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** Get full ride detail (used by payment-service, compliance-service). */
    @GetMapping("/{rideId}")
    public ResponseEntity<ApiResponse<RideResponse>> getRide(@PathVariable UUID rideId) {
        Ride ride = rideService.getRide(rideId);
        return ResponseEntity.ok(ApiResponse.ok(rideMapper.toResponse(ride)));
    }

    /** Ride history by userId + cityId (for user-service destination suggestions). */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<RideHistorySummaryDto>>> getRideHistory(
            @RequestParam UUID userId,
            @RequestParam UUID cityId,
            @RequestParam(defaultValue = "5") int limit) {
        Page<Ride> rides = rideService.getRideHistoryByCity(userId, cityId, limit);
        return ResponseEntity.ok(
                ApiResponse.ok(rideMapper.toHistorySummaryList(rides.getContent())));
    }
}
