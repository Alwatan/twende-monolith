package tz.co.twende.analytics.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.analytics.dto.AdminOverviewDto;
import tz.co.twende.analytics.dto.CountryMetricsDto;
import tz.co.twende.analytics.service.AdminAnalyticsService;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.common.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/analytics/admin")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AdminOverviewDto>> getOverview(
            @RequestHeader("X-User-Role") String role) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.ok(adminAnalyticsService.getOverview()));
    }

    @GetMapping("/countries/{code}")
    public ResponseEntity<ApiResponse<CountryMetricsDto>> getCountryMetrics(
            @RequestHeader("X-User-Role") String role, @PathVariable String code) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.ok(adminAnalyticsService.getCountryMetrics(code)));
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new UnauthorizedException("Admin access required");
        }
    }
}
