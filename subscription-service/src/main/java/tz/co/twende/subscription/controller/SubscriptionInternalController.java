package tz.co.twende.subscription.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.subscription.dto.RevenueModelDto;
import tz.co.twende.subscription.service.RevenueModelService;
import tz.co.twende.subscription.service.SubscriptionService;

@RestController
@RequestMapping("/internal/subscriptions")
@RequiredArgsConstructor
public class SubscriptionInternalController {

    private final SubscriptionService subscriptionService;
    private final RevenueModelService revenueModelService;

    @GetMapping("/{driverId}/active")
    public ResponseEntity<ApiResponse<Boolean>> hasActiveSubscription(@PathVariable UUID driverId) {
        boolean active = revenueModelService.hasActiveRevenueModel(driverId);
        return ResponseEntity.ok(ApiResponse.ok(active));
    }

    @GetMapping("/{driverId}/revenue-model")
    public ResponseEntity<ApiResponse<RevenueModelDto>> getRevenueModel(
            @PathVariable UUID driverId) {
        RevenueModelDto model = revenueModelService.getRevenueModel(driverId);
        return ResponseEntity.ok(ApiResponse.ok(model));
    }
}
