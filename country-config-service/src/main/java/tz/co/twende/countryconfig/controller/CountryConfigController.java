package tz.co.twende.countryconfig.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.countryconfig.dto.CountryConfigDto;
import tz.co.twende.countryconfig.dto.CreateCityRequest;
import tz.co.twende.countryconfig.dto.OperatingCityDto;
import tz.co.twende.countryconfig.dto.UpdateCountryConfigRequest;
import tz.co.twende.countryconfig.dto.VehicleTypeConfigDto;
import tz.co.twende.countryconfig.service.CountryConfigService;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class CountryConfigController {

    private final CountryConfigService countryConfigService;

    // ---- Public endpoints (no auth check) ----

    @GetMapping("/{countryCode}")
    public ResponseEntity<ApiResponse<CountryConfigDto>> getConfig(
            @PathVariable String countryCode) {
        CountryConfigDto config = countryConfigService.getConfig(countryCode.toUpperCase());
        return ResponseEntity.ok(ApiResponse.ok(config));
    }

    @GetMapping("/{countryCode}/vehicle-types")
    public ResponseEntity<ApiResponse<List<VehicleTypeConfigDto>>> getVehicleTypes(
            @PathVariable String countryCode) {
        List<VehicleTypeConfigDto> vehicleTypes =
                countryConfigService.getVehicleTypes(countryCode.toUpperCase());
        return ResponseEntity.ok(ApiResponse.ok(vehicleTypes));
    }

    @GetMapping("/{countryCode}/cities")
    public ResponseEntity<ApiResponse<List<OperatingCityDto>>> getCities(
            @PathVariable String countryCode) {
        List<OperatingCityDto> cities = countryConfigService.getCities(countryCode.toUpperCase());
        return ResponseEntity.ok(ApiResponse.ok(cities));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<String>>> getActiveCountries() {
        List<String> activeCodes = countryConfigService.getActiveCountries();
        return ResponseEntity.ok(ApiResponse.ok(activeCodes));
    }

    // ---- Admin endpoints (require X-User-Role == "ADMIN") ----

    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<List<CountryConfigDto>>> getAllConfigs(
            HttpServletRequest request) {
        requireAdmin(request);
        List<CountryConfigDto> configs = countryConfigService.getAllConfigs();
        return ResponseEntity.ok(ApiResponse.ok(configs));
    }

    @PutMapping("/{countryCode}")
    public ResponseEntity<ApiResponse<Void>> updateConfig(
            @PathVariable String countryCode,
            @Valid @RequestBody UpdateCountryConfigRequest updateRequest,
            HttpServletRequest request) {
        requireAdmin(request);
        countryConfigService.updateConfig(countryCode.toUpperCase(), updateRequest);
        return ResponseEntity.ok(ApiResponse.ok(null, "Country config updated"));
    }

    @PatchMapping("/{countryCode}/features")
    public ResponseEntity<ApiResponse<Void>> updateFeatures(
            @PathVariable String countryCode,
            @RequestBody Map<String, Object> features,
            HttpServletRequest request) {
        requireAdmin(request);
        countryConfigService.updateFeatures(countryCode.toUpperCase(), features);
        return ResponseEntity.ok(ApiResponse.ok(null, "Features updated"));
    }

    @PatchMapping("/{countryCode}/status")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable String countryCode,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        requireAdmin(request);
        String status = body.get("status");
        countryConfigService.updateStatus(countryCode.toUpperCase(), status);
        return ResponseEntity.ok(ApiResponse.ok(null, "Status updated"));
    }

    @PutMapping("/{countryCode}/vehicle-types/{id}")
    public ResponseEntity<ApiResponse<Void>> updateVehicleType(
            @PathVariable String countryCode,
            @PathVariable UUID id,
            @Valid @RequestBody VehicleTypeConfigDto dto,
            HttpServletRequest request) {
        requireAdmin(request);
        countryConfigService.updateVehicleType(countryCode.toUpperCase(), id, dto);
        return ResponseEntity.ok(ApiResponse.ok(null, "Vehicle type config updated"));
    }

    @PostMapping("/{countryCode}/cities")
    public ResponseEntity<ApiResponse<OperatingCityDto>> createCity(
            @PathVariable String countryCode,
            @Valid @RequestBody CreateCityRequest createRequest,
            HttpServletRequest request) {
        requireAdmin(request);
        OperatingCityDto city =
                countryConfigService.createCity(countryCode.toUpperCase(), createRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(city, "City created"));
    }

    private void requireAdmin(HttpServletRequest request) {
        String role = request.getHeader("X-User-Role");
        if (!"ADMIN".equals(role)) {
            throw new UnauthorizedException("Admin access required");
        }
    }
}
