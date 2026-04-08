package tz.co.twende.loyalty.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.loyalty.dto.request.UpdateLoyaltyRuleRequest;
import tz.co.twende.loyalty.dto.response.FreeRideOfferDto;
import tz.co.twende.loyalty.dto.response.LoyaltyRuleDto;
import tz.co.twende.loyalty.dto.response.RiderProgressDto;
import tz.co.twende.loyalty.mapper.LoyaltyMapper;
import tz.co.twende.loyalty.service.LoyaltyService;

@RestController
@RequestMapping("/api/v1/loyalty")
@RequiredArgsConstructor
public class LoyaltyController {

    private final LoyaltyService loyaltyService;
    private final LoyaltyMapper loyaltyMapper;

    @GetMapping("/progress")
    public ResponseEntity<ApiResponse<List<RiderProgressDto>>> getProgress(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        loyaltyMapper.toRiderProgressDtoList(loyaltyService.getProgress(userId))));
    }

    @GetMapping("/offers")
    public ResponseEntity<ApiResponse<List<FreeRideOfferDto>>> getOffers(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        loyaltyMapper.toFreeRideOfferDtoList(
                                loyaltyService.getAvailableOffers(userId))));
    }

    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<List<LoyaltyRuleDto>>> getRules(
            @RequestParam(defaultValue = "TZ") String countryCode) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        loyaltyMapper.toLoyaltyRuleDtoList(loyaltyService.getRules(countryCode))));
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<LoyaltyRuleDto>> updateRule(
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLoyaltyRuleRequest request) {
        if (!"ADMIN".equals(role)) {
            throw new UnauthorizedException("Only admins can update loyalty rules");
        }
        return ResponseEntity.ok(
                ApiResponse.ok(
                        loyaltyMapper.toLoyaltyRuleDto(loyaltyService.updateRule(id, request))));
    }
}
