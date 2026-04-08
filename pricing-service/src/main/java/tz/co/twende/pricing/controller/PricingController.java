package tz.co.twende.pricing.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.pricing.dto.*;
import tz.co.twende.pricing.service.PricingService;
import tz.co.twende.pricing.service.SurgeService;

@RestController
public class PricingController {

    private final PricingService pricingService;
    private final SurgeService surgeService;

    public PricingController(PricingService pricingService, SurgeService surgeService) {
        this.pricingService = pricingService;
        this.surgeService = surgeService;
    }

    @PostMapping("/api/v1/pricing/estimate")
    public ResponseEntity<ApiResponse<EstimateResponse>> estimate(
            @Valid @RequestBody EstimateRequest request) {
        EstimateResponse response = pricingService.calculateEstimate(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/internal/pricing/calculate")
    public ResponseEntity<ApiResponse<CalculateResponse>> calculate(
            @Valid @RequestBody CalculateRequest request) {
        CalculateResponse response = pricingService.calculateFinal(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/api/v1/pricing/surge/{countryCode}/{vehicleType}")
    public ResponseEntity<ApiResponse<SurgeResponse>> getSurge(
            @PathVariable String countryCode, @PathVariable String vehicleType) {
        SurgeResponse response = surgeService.getSurgeResponse(countryCode, vehicleType);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
