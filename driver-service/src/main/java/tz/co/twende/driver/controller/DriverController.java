package tz.co.twende.driver.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.driver.dto.request.RegisterVehicleRequest;
import tz.co.twende.driver.dto.request.UpdateDriverRequest;
import tz.co.twende.driver.dto.request.UpdateStatusRequest;
import tz.co.twende.driver.dto.request.UploadDocumentRequest;
import tz.co.twende.driver.dto.response.*;
import tz.co.twende.driver.service.DocumentService;
import tz.co.twende.driver.service.DriverService;
import tz.co.twende.driver.service.VehicleService;

@RestController
@RequestMapping("/api/v1/drivers/me")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;
    private final DocumentService documentService;
    private final VehicleService vehicleService;

    @GetMapping
    public ResponseEntity<ApiResponse<DriverProfileDto>> getProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        validateDriverRole(role);
        return ResponseEntity.ok(
                ApiResponse.ok(driverService.getProfile(userId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<DriverProfileDto>> updateProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody UpdateDriverRequest request) {
        validateDriverRole(role);
        return ResponseEntity.ok(
                ApiResponse.ok(
                        driverService.updateProfile(userId, request)));
    }

    @PutMapping("/status")
    public ResponseEntity<ApiResponse<DriverProfileDto>> updateStatus(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody UpdateStatusRequest request) {
        validateDriverRole(role);
        return ResponseEntity.ok(
                ApiResponse.ok(
                        driverService.updateStatus(
                                userId, request.getStatus())));
    }

    @PostMapping("/documents")
    public ResponseEntity<ApiResponse<DriverDocumentDto>> uploadDocument(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader("X-Country-Code") String countryCode,
            @Valid @RequestBody UploadDocumentRequest request) {
        validateDriverRole(role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.ok(
                                documentService.uploadDocument(
                                        userId,
                                        countryCode,
                                        request)));
    }

    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<List<DriverDocumentDto>>>
            listDocuments(
                    @RequestHeader("X-User-Id") UUID userId,
                    @RequestHeader("X-User-Role") String role) {
        validateDriverRole(role);
        return ResponseEntity.ok(
                ApiResponse.ok(documentService.listDocuments(userId)));
    }

    @PostMapping("/vehicles")
    public ResponseEntity<ApiResponse<DriverVehicleDto>> registerVehicle(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader("X-Country-Code") String countryCode,
            @Valid @RequestBody RegisterVehicleRequest request) {
        validateDriverRole(role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.ok(
                                vehicleService.registerVehicle(
                                        userId,
                                        countryCode,
                                        request)));
    }

    @GetMapping("/vehicles")
    public ResponseEntity<ApiResponse<List<DriverVehicleDto>>> listVehicles(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        validateDriverRole(role);
        return ResponseEntity.ok(
                ApiResponse.ok(vehicleService.listVehicles(userId)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DriverSummaryDto>> getSummary(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        validateDriverRole(role);
        return ResponseEntity.ok(
                ApiResponse.ok(driverService.getSummary(userId)));
    }

    private void validateDriverRole(String role) {
        if (!"DRIVER".equals(role)) {
            throw new UnauthorizedException(
                    "Access denied. Driver role required.");
        }
    }
}
