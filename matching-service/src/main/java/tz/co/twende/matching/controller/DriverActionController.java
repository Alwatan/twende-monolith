package tz.co.twende.matching.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.matching.dto.AcceptRejectResponse;
import tz.co.twende.matching.service.AcceptanceService;

@RestController
@RequestMapping("/internal/matching/driver-actions")
@RequiredArgsConstructor
@Slf4j
public class DriverActionController {

    private final AcceptanceService acceptanceService;

    @PutMapping("/{rideId}/accept")
    public ResponseEntity<ApiResponse<AcceptRejectResponse>> acceptRide(
            @PathVariable UUID rideId,
            @RequestHeader("X-User-Id") UUID driverId,
            @RequestHeader(value = "X-Country-Code", defaultValue = "TZ") String countryCode) {
        log.info("Driver {} accepting ride {}", driverId, rideId);
        AcceptRejectResponse result = acceptanceService.acceptOffer(rideId, driverId, countryCode);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PutMapping("/{rideId}/reject")
    public ResponseEntity<ApiResponse<AcceptRejectResponse>> rejectRide(
            @PathVariable UUID rideId,
            @RequestHeader("X-User-Id") UUID driverId,
            @RequestHeader(value = "X-Country-Code", defaultValue = "TZ") String countryCode) {
        log.info("Driver {} rejecting ride {}", driverId, rideId);
        AcceptRejectResponse result = acceptanceService.rejectOffer(rideId, driverId, countryCode);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
