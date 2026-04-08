package tz.co.twende.countryconfig.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.countryconfig.dto.FlatFeeConfigDto;
import tz.co.twende.countryconfig.dto.UpdateFlatFeeRequest;
import tz.co.twende.countryconfig.service.FlatFeeConfigService;

@RestController
@RequiredArgsConstructor
public class FlatFeeConfigController {

    private final FlatFeeConfigService flatFeeConfigService;

    // ---- Public endpoint ----

    @GetMapping("/api/v1/config/{countryCode}/flat-fee")
    public ResponseEntity<ApiResponse<List<FlatFeeConfigDto>>> getFlatFeeConfigs(
            @PathVariable String countryCode) {
        List<FlatFeeConfigDto> configs =
                flatFeeConfigService.getFlatFeeConfigs(countryCode.toUpperCase());
        return ResponseEntity.ok(ApiResponse.ok(configs));
    }

    // ---- Internal endpoint (service-to-service) ----

    @GetMapping("/internal/config/{countryCode}/flat-fee/{serviceCategory}")
    public ResponseEntity<ApiResponse<FlatFeeConfigDto>> getFlatFeeConfig(
            @PathVariable String countryCode, @PathVariable String serviceCategory) {
        FlatFeeConfigDto config =
                flatFeeConfigService.getFlatFeeConfig(
                        countryCode.toUpperCase(), serviceCategory.toUpperCase());
        return ResponseEntity.ok(ApiResponse.ok(config));
    }

    // ---- Admin endpoint ----

    @PutMapping("/api/v1/config/{countryCode}/flat-fee/{serviceCategory}")
    public ResponseEntity<ApiResponse<FlatFeeConfigDto>> updateFlatFeeConfig(
            @PathVariable String countryCode,
            @PathVariable String serviceCategory,
            @Valid @RequestBody UpdateFlatFeeRequest request,
            HttpServletRequest httpRequest) {
        requireAdmin(httpRequest);
        FlatFeeConfigDto updated =
                flatFeeConfigService.updateFlatFeeConfig(
                        countryCode.toUpperCase(), serviceCategory.toUpperCase(), request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Flat fee config updated"));
    }

    private void requireAdmin(HttpServletRequest request) {
        String role = request.getHeader("X-User-Role");
        if (!"ADMIN".equals(role)) {
            throw new UnauthorizedException("Admin access required");
        }
    }
}
