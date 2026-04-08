package tz.co.twende.driver.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.driver.dto.response.ActiveVehicleDto;
import tz.co.twende.driver.dto.response.DriverProfileDto;
import tz.co.twende.driver.dto.response.DriverServiceCategoriesDto;
import tz.co.twende.driver.service.DriverService;
import tz.co.twende.driver.service.VehicleService;

@RestController
@RequestMapping("/internal/drivers")
@RequiredArgsConstructor
public class DriverInternalController {

    private final DriverService driverService;
    private final VehicleService vehicleService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DriverProfileDto>> getDriver(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(driverService.getProfile(id)));
    }

    @GetMapping("/{id}/active-vehicle")
    public ResponseEntity<ApiResponse<ActiveVehicleDto>> getActiveVehicle(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.getActiveVehicle(id)));
    }

    @GetMapping("/{id}/service-categories")
    public ResponseEntity<ApiResponse<DriverServiceCategoriesDto>> getServiceCategories(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(driverService.getServiceCategories(id)));
    }
}
