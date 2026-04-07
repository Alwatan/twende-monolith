package tz.co.twende.user.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.user.dto.CreateSavedPlaceRequest;
import tz.co.twende.user.dto.SavedPlaceDto;
import tz.co.twende.user.service.SavedPlaceService;

@RestController
@RequestMapping("/api/v1/users/me/saved-places")
@RequiredArgsConstructor
public class SavedPlaceController {

    private final SavedPlaceService savedPlaceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SavedPlaceDto>>> getPlaces(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(savedPlaceService.getPlaces(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SavedPlaceDto>> createPlace(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-Country-Code") String countryCode,
            @Valid @RequestBody CreateSavedPlaceRequest request) {
        SavedPlaceDto created = savedPlaceService.createPlace(userId, countryCode, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePlace(
            @RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id) {
        savedPlaceService.deletePlace(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Saved place deleted successfully"));
    }
}
