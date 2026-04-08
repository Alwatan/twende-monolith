package tz.co.twende.subscription.controller;

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
import tz.co.twende.subscription.dto.PurchaseRequest;
import tz.co.twende.subscription.dto.SubscriptionDto;
import tz.co.twende.subscription.dto.SubscriptionPlanDto;
import tz.co.twende.subscription.service.SubscriptionService;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<SubscriptionPlanDto>>> getPlans(
            @RequestHeader("X-Country-Code") String countryCode,
            @RequestParam(required = false) String vehicleType) {
        List<SubscriptionPlanDto> plans = subscriptionService.getPlans(countryCode, vehicleType);
        return ResponseEntity.ok(ApiResponse.ok(plans));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SubscriptionDto>> getCurrentSubscription(
            @RequestHeader("X-User-Id") UUID driverId) {
        SubscriptionDto subscription = subscriptionService.getCurrentSubscription(driverId);
        return ResponseEntity.ok(ApiResponse.ok(subscription));
    }

    @PostMapping("/purchase")
    public ResponseEntity<ApiResponse<SubscriptionDto>> purchase(
            @RequestHeader("X-User-Id") UUID driverId,
            @RequestHeader("X-Country-Code") String countryCode,
            @Valid @RequestBody PurchaseRequest request) {
        SubscriptionDto subscription =
                subscriptionService.purchase(
                        driverId, countryCode, request.getPlanId(), request.getPaymentMethod());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(subscription));
    }

    @GetMapping("/me/history")
    public ResponseEntity<ApiResponse<Page<SubscriptionDto>>> getHistory(
            @RequestHeader("X-User-Id") UUID driverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest =
                PageRequest.of(
                        page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SubscriptionDto> history = subscriptionService.getHistory(driverId, pageRequest);
        return ResponseEntity.ok(ApiResponse.ok(history));
    }
}
