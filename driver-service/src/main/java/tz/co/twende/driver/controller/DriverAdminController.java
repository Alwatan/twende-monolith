package tz.co.twende.driver.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.common.enums.DriverStatus;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.common.response.PagedResponse;
import tz.co.twende.driver.dto.request.ApprovalRequest;
import tz.co.twende.driver.dto.request.DocumentVerifyRequest;
import tz.co.twende.driver.dto.response.*;
import tz.co.twende.driver.service.DocumentService;
import tz.co.twende.driver.service.DriverApprovalService;
import tz.co.twende.driver.service.DriverService;
import tz.co.twende.driver.service.VehicleService;

@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
public class DriverAdminController {

    private final DriverService driverService;
    private final DriverApprovalService approvalService;
    private final DocumentService documentService;
    private final VehicleService vehicleService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<DriverProfileDto>>> listDrivers(
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) DriverStatus status,
            @RequestParam(required = false) String countryCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validateAdminRole(role);
        Page<DriverProfileDto> drivers = driverService.listDrivers(status, countryCode, page, size);
        return ResponseEntity.ok(ApiResponse.ok(PagedResponse.from(drivers)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DriverDetailDto>> getDriverDetail(
            @RequestHeader("X-User-Role") String role, @PathVariable UUID id) {
        validateAdminRole(role);
        DriverProfileDto profile = driverService.getProfile(id);
        List<DriverVehicleDto> vehicles = vehicleService.listVehicles(id);
        List<DriverDocumentDto> documents = documentService.listDocuments(id);
        DriverDetailDto detail =
                DriverDetailDto.builder()
                        .profile(profile)
                        .vehicles(vehicles)
                        .documents(documents)
                        .build();
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    @PutMapping("/{id}/approval")
    public ResponseEntity<ApiResponse<DriverProfileDto>> processApproval(
            @RequestHeader("X-User-Id") UUID adminId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalRequest request) {
        validateAdminRole(role);
        return ResponseEntity.ok(
                ApiResponse.ok(approvalService.processApproval(id, adminId, request)));
    }

    @PutMapping("/{id}/documents/{docId}/verify")
    public ResponseEntity<ApiResponse<DriverDocumentDto>> verifyDocument(
            @RequestHeader("X-User-Id") UUID adminId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID id,
            @PathVariable UUID docId,
            @Valid @RequestBody DocumentVerifyRequest request) {
        validateAdminRole(role);
        return ResponseEntity.ok(
                ApiResponse.ok(documentService.verifyDocument(docId, adminId, request)));
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<ApiResponse<DriverProfileDto>> suspendDriver(
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        validateAdminRole(role);
        return ResponseEntity.ok(ApiResponse.ok(approvalService.suspendDriver(id, reason)));
    }

    private void validateAdminRole(String role) {
        if (!"ADMIN".equals(role)) {
            throw new UnauthorizedException("Access denied. Admin role required.");
        }
    }
}
