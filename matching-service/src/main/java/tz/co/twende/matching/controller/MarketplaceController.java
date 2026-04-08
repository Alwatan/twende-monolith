package tz.co.twende.matching.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.matching.dto.MarketplaceBookingDto;
import tz.co.twende.matching.service.MarketplaceService;

@RestController
@RequiredArgsConstructor
@Slf4j
public class MarketplaceController {

    private final MarketplaceService marketplaceService;

    @GetMapping("/api/v1/marketplace/bookings")
    public ResponseEntity<ApiResponse<List<MarketplaceBookingDto>>> getAvailableBookings(
            @RequestParam(defaultValue = "CHARTER") String serviceCategory,
            @RequestParam String vehicleType,
            @RequestParam(required = false) String qualityTier,
            @RequestParam(required = false) Instant fromDate,
            @RequestParam(required = false) Instant toDate,
            @RequestHeader(value = "X-Country-Code", defaultValue = "TZ") String countryCode) {
        log.info(
                "Driver browsing marketplace: {} {} in {}",
                serviceCategory,
                vehicleType,
                countryCode);
        List<MarketplaceBookingDto> bookings =
                marketplaceService.getAvailableBookings(
                        countryCode, serviceCategory, vehicleType, qualityTier, fromDate, toDate);
        return ResponseEntity.ok(ApiResponse.ok(bookings));
    }

    @PostMapping("/api/v1/marketplace/bookings/{id}/request")
    public ResponseEntity<ApiResponse<String>> requestBooking(
            @PathVariable UUID id, @RequestHeader("X-User-Id") UUID driverId) {
        log.info("Driver {} requesting booking {}", driverId, id);
        marketplaceService.requestBooking(id, driverId);
        return ResponseEntity.ok(ApiResponse.ok("Booking request submitted"));
    }

    @PostMapping("/internal/marketplace/bookings/{id}/confirm/{driverId}")
    public ResponseEntity<ApiResponse<String>> confirmDriver(
            @PathVariable UUID id, @PathVariable UUID driverId) {
        log.info("Confirming driver {} for booking {}", driverId, id);
        marketplaceService.confirmDriver(id, driverId);
        return ResponseEntity.ok(ApiResponse.ok("Driver confirmed for booking"));
    }
}
