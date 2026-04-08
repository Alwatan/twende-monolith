package tz.co.twende.subscription.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.subscription.service.SubscriptionService;

@RestController
@RequestMapping("/internal/subscriptions")
@RequiredArgsConstructor
public class SubscriptionInternalController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/{driverId}/active")
    public ResponseEntity<ApiResponse<Boolean>> hasActiveSubscription(@PathVariable UUID driverId) {
        boolean active = subscriptionService.hasActiveSubscription(driverId);
        return ResponseEntity.ok(ApiResponse.ok(active));
    }
}
