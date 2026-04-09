package tz.co.twende.compliance.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.common.response.PagedResponse;
import tz.co.twende.compliance.dto.SubmissionStatsDto;
import tz.co.twende.compliance.dto.TripReportDto;
import tz.co.twende.compliance.entity.TripReport;
import tz.co.twende.compliance.service.ComplianceService;

@RestController
@RequestMapping("/api/v1/compliance/reports")
@RequiredArgsConstructor
public class ComplianceController {

    private final ComplianceService complianceService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TripReportDto>>> getReports(
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) Boolean submitted,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        Page<TripReport> reports =
                complianceService.getReports(
                        countryCode,
                        submitted,
                        from,
                        to,
                        PageRequest.of(page, Math.min(size, 100)));
        Page<TripReportDto> dtoPage = reports.map(this::toDto);
        return ResponseEntity.ok(ApiResponse.ok(PagedResponse.from(dtoPage)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TripReportDto>> getReport(
            @RequestHeader("X-User-Role") String role, @PathVariable UUID id) {
        requireAdmin(role);
        TripReport report = complianceService.getReportById(id);
        return ResponseEntity.ok(ApiResponse.ok(toDto(report)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<List<SubmissionStatsDto>>> getStats(
            @RequestHeader("X-User-Role") String role) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.ok(complianceService.getStats()));
    }

    @PostMapping("/retry")
    public ResponseEntity<ApiResponse<Integer>> retrySubmissions(
            @RequestHeader("X-User-Role") String role, @RequestParam String countryCode) {
        requireAdmin(role);
        int processed = complianceService.retryFailedSubmissions(countryCode);
        return ResponseEntity.ok(ApiResponse.ok(processed, "Processed " + processed + " reports"));
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new UnauthorizedException("Admin role required");
        }
    }

    private TripReportDto toDto(TripReport r) {
        return TripReportDto.builder()
                .id(r.getId())
                .rideId(r.getRideId())
                .countryCode(r.getCountryCode())
                .driverId(r.getDriverId())
                .riderId(r.getRiderId())
                .vehicleType(r.getVehicleType())
                .pickupLat(r.getPickupLat())
                .pickupLng(r.getPickupLng())
                .dropoffLat(r.getDropoffLat())
                .dropoffLng(r.getDropoffLng())
                .distanceMetres(r.getDistanceMetres())
                .durationSeconds(r.getDurationSeconds())
                .fare(r.getFare())
                .currency(r.getCurrency())
                .startedAt(r.getStartedAt())
                .completedAt(r.getCompletedAt())
                .submitted(r.isSubmitted())
                .submittedAt(r.getSubmittedAt())
                .submissionRef(r.getSubmissionRef())
                .submissionError(r.getSubmissionError())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
