package tz.co.twende.rating.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.rating.dto.RatingDto;
import tz.co.twende.rating.dto.RatingSummaryDto;
import tz.co.twende.rating.dto.SubmitRatingRequest;
import tz.co.twende.rating.service.RatingService;

@RestController
@RequestMapping("/api/v1/ratings")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @PostMapping
    public ResponseEntity<ApiResponse<RatingDto>> submitRating(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody SubmitRatingRequest request) {
        RatingDto dto = ratingService.submitRating(userId, role, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto));
    }

    @GetMapping("/driver/{driverId}")
    public ResponseEntity<ApiResponse<RatingSummaryDto>> getDriverRating(
            @PathVariable UUID driverId) {
        return ResponseEntity.ok(ApiResponse.ok(ratingService.getDriverRatingSummary(driverId)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<RatingSummaryDto>> getMyRating(
            @RequestHeader("X-User-Id") UUID userId, @RequestHeader("X-User-Role") String role) {
        RatingSummaryDto summary;
        if ("DRIVER".equals(role)) {
            summary = ratingService.getDriverRatingSummary(userId);
        } else {
            summary = ratingService.getRiderRatingSummary(userId);
        }
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }
}
