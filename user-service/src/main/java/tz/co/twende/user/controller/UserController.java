package tz.co.twende.user.controller;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.common.response.PagedResponse;
import tz.co.twende.user.dto.DestinationSuggestionsDto;
import tz.co.twende.user.dto.RideHistoryResponse;
import tz.co.twende.user.dto.UpdateProfileRequest;
import tz.co.twende.user.dto.UserProfileDto;
import tz.co.twende.user.service.DestinationSuggestionService;
import tz.co.twende.user.service.UserService;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final DestinationSuggestionService destinationSuggestionService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileDto>> getProfile(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(userId)));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileDto>> updateProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(userId, request)));
    }

    @GetMapping("/me/ride-history")
    public ResponseEntity<ApiResponse<PagedResponse<RideHistoryResponse>>> getRideHistory(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getRideHistory(userId, page, size)));
    }

    @GetMapping("/me/suggestions")
    public ResponseEntity<ApiResponse<DestinationSuggestionsDto>> getSuggestions(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam BigDecimal lat,
            @RequestParam BigDecimal lng) {
        return ResponseEntity.ok(
                ApiResponse.ok(destinationSuggestionService.getSuggestions(userId, lat, lng)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserProfileDto>> getUserById(
            @RequestHeader("X-User-Role") String role, @PathVariable UUID id) {
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("Access denied. Admin role required."));
        }
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(id)));
    }
}
