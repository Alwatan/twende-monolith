package tz.co.twende.location.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.location.dto.GeocodingResult;
import tz.co.twende.location.dto.LatLng;
import tz.co.twende.location.dto.PlaceResult;
import tz.co.twende.location.service.AutocompleteService;
import tz.co.twende.location.service.GeocodingService;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class GeocodingController {

    private final GeocodingService geocodingService;
    private final AutocompleteService autocompleteService;

    @GetMapping("/geocode")
    public ResponseEntity<ApiResponse<GeocodingResult>> geocode(
            @RequestParam String address, @RequestParam UUID cityId) {
        GeocodingResult result = geocodingService.geocode(address, null, cityId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/reverse")
    public ResponseEntity<ApiResponse<GeocodingResult>> reverseGeocode(
            @RequestParam BigDecimal lat, @RequestParam BigDecimal lng, @RequestParam UUID cityId) {
        GeocodingResult result = geocodingService.reverseGeocode(new LatLng(lat, lng), cityId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<ApiResponse<List<PlaceResult>>> autocomplete(
            @RequestParam String q,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng,
            @RequestParam UUID cityId,
            @RequestParam(defaultValue = "TZ") String countryCode,
            @RequestParam(defaultValue = "5") int limit) {
        LatLng bias = (lat != null && lng != null) ? new LatLng(lat, lng) : null;
        List<PlaceResult> results = autocompleteService.search(q, bias, countryCode, cityId, limit);
        return ResponseEntity.ok(ApiResponse.ok(results));
    }
}
