package tz.co.twende.analytics.controller;

import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.analytics.dto.DriverEarningsDto;
import tz.co.twende.analytics.dto.DriverTripStatsDto;
import tz.co.twende.analytics.service.DriverAnalyticsService;
import tz.co.twende.common.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/analytics/driver")
@RequiredArgsConstructor
public class DriverAnalyticsController {

    private final DriverAnalyticsService driverAnalyticsService;

    @GetMapping("/earnings")
    public ResponseEntity<ApiResponse<DriverEarningsDto>> getEarnings(
            @RequestHeader("X-User-Id") UUID driverId,
            @RequestParam(defaultValue = "WEEKLY") String period) {
        return ResponseEntity.ok(
                ApiResponse.ok(driverAnalyticsService.getEarnings(driverId, period)));
    }

    @GetMapping("/trips")
    public ResponseEntity<ApiResponse<DriverTripStatsDto>> getTripStats(
            @RequestHeader("X-User-Id") UUID driverId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        return ResponseEntity.ok(
                ApiResponse.ok(driverAnalyticsService.getTripStats(driverId, from, to)));
    }
}
