package tz.co.twende.ride.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
public class RideController {

    private final RideService rideService;
    private final FareBoostService fareBoostService;
    private final RideMapper rideMapper;

    /** Create a new ride request (rider). */
    @PostMapping
    public ResponseEntity<ApiResponse<RideResponse>> createRide(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-Country-Code") String countryCode,
            @Valid @RequestBody CreateRideRequest request) {
        Ride ride = rideService.createRide(userId, countryCode, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(rideMapper.toResponse(ride)));
    }

    /** Get ride details by ID. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RideResponse>> getRide(@PathVariable UUID id) {
        Ride ride = rideService.getRide(id);
        return ResponseEntity.ok(ApiResponse.ok(rideMapper.toResponse(ride)));
    }

    /** Boost fare on a ride (rider). */
    @PutMapping("/{id}/boost")
    public ResponseEntity<ApiResponse<RideResponse>> boostFare(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody BoostFareRequest request) {
        Ride ride = fareBoostService.boostFare(id, userId, request.getBoostAmount());
        return ResponseEntity.ok(ApiResponse.ok(rideMapper.toResponse(ride)));
    }

    /** Cancel a ride (rider or driver). */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<RideResponse>> cancelRide(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @RequestBody(required = false) CancelRideRequest request) {
        String reason = request != null ? request.getReason() : null;
        Ride ride = rideService.cancelRide(id, userId, role, reason);
        return ResponseEntity.ok(ApiResponse.ok(rideMapper.toResponse(ride)));
    }

    /** Mark driver as arrived. */
    @PutMapping("/{id}/arrived")
    public ResponseEntity<ApiResponse<RideResponse>> driverArrived(
            @PathVariable UUID id, @RequestHeader("X-User-Id") UUID driverId) {
        Ride ride = rideService.driverArrived(id, driverId);
        return ResponseEntity.ok(ApiResponse.ok(rideMapper.toResponse(ride)));
    }

    /** Start trip with OTP verification (driver). */
    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<RideResponse>> startTrip(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID driverId,
            @Valid @RequestBody StartTripRequest request) {
        Ride ride = rideService.startTrip(id, driverId, request.getOtp());
        return ResponseEntity.ok(ApiResponse.ok(rideMapper.toResponse(ride)));
    }

    /** Complete a trip (driver). */
    @PutMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<RideResponse>> completeTrip(
            @PathVariable UUID id, @RequestHeader("X-User-Id") UUID driverId) {
        Ride ride = rideService.completeTrip(id, driverId);
        return ResponseEntity.ok(ApiResponse.ok(rideMapper.toResponse(ride)));
    }

    /** Resend trip start OTP (rider). */
    @PostMapping("/{id}/otp/resend")
    public ResponseEntity<ApiResponse<RideResponse>> resendOtp(
            @PathVariable UUID id, @RequestHeader("X-User-Id") UUID riderId) {
        Ride ride = rideService.resendOtp(id, riderId);
        return ResponseEntity.ok(
                ApiResponse.ok(rideMapper.toResponse(ride), "New OTP sent to rider"));
    }

    /** Get rider's active ride(s). */
    @GetMapping("/me/active")
    public ResponseEntity<ApiResponse<List<RideResponse>>> getActiveRides(
            @RequestHeader("X-User-Id") UUID userId, @RequestHeader("X-User-Role") String role) {
        List<Ride> rides;
        if ("DRIVER".equals(role)) {
            rides = rideService.getActiveRidesForDriver(userId);
        } else {
            rides = rideService.getActiveRidesForRider(userId);
        }
        return ResponseEntity.ok(ApiResponse.ok(rideMapper.toResponseList(rides)));
    }

    /** Get ride history (paginated). */
    @GetMapping("/me/history")
    public ResponseEntity<ApiResponse<Page<RideResponse>>> getRideHistory(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Ride> rides =
                rideService.getRideHistory(
                        userId,
                        PageRequest.of(
                                page, Math.min(size, 100), Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.ok(rides.map(rideMapper::toResponse)));
    }
}
