package tz.co.twende.location.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.location.dto.NearbyDriverResponse;
import tz.co.twende.location.entity.TripTrace;
import tz.co.twende.location.service.LocationService;
import tz.co.twende.location.service.TripTraceService;

@RestController
@RequestMapping("/internal/location")
@RequiredArgsConstructor
public class InternalLocationController {

    private final LocationService locationService;
    private final TripTraceService tripTraceService;

    @GetMapping("/drivers/nearby")
    public ResponseEntity<ApiResponse<List<NearbyDriverResponse>>> nearbyDrivers(
            @RequestParam String countryCode,
            @RequestParam String vehicleType,
            @RequestParam BigDecimal lat,
            @RequestParam BigDecimal lng,
            @RequestParam BigDecimal radiusKm) {
        List<NearbyDriverResponse> drivers =
                locationService.findNearbyDrivers(countryCode, vehicleType, lat, lng, radiusKm);
        return ResponseEntity.ok(ApiResponse.ok(drivers));
    }

    @GetMapping("/driver/{driverId}")
    public ResponseEntity<ApiResponse<NearbyDriverResponse>> driverLocation(
            @PathVariable UUID driverId) {
        NearbyDriverResponse location = locationService.getDriverLocation(driverId);
        return ResponseEntity.ok(ApiResponse.ok(location));
    }

    @GetMapping("/rides/{rideId}/trace")
    public ResponseEntity<ApiResponse<TripTrace>> tripTrace(@PathVariable UUID rideId) {
        TripTrace trace = tripTraceService.getTrace(rideId);
        return ResponseEntity.ok(ApiResponse.ok(trace));
    }
}
