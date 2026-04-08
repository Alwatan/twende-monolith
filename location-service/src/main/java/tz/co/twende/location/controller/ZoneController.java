package tz.co.twende.location.controller;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.location.dto.CreateZoneRequest;
import tz.co.twende.location.dto.UpdateZoneRequest;
import tz.co.twende.location.dto.ZoneCheckResponse;
import tz.co.twende.location.dto.ZoneDto;
import tz.co.twende.location.entity.Zone;
import tz.co.twende.location.mapper.ZoneMapper;
import tz.co.twende.location.service.GeofenceService;
import tz.co.twende.location.service.ZoneService;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;
    private final GeofenceService geofenceService;
    private final ZoneMapper zoneMapper;

    @GetMapping("/zones/check")
    public ResponseEntity<ApiResponse<ZoneCheckResponse>> checkZones(
            @RequestParam BigDecimal lat, @RequestParam BigDecimal lng, @RequestParam UUID cityId) {
        List<Zone> zones = geofenceService.findAllZonesContaining(cityId, lat, lng);
        boolean inServiceArea = zones.stream().anyMatch(z -> "OPERATING".equals(z.getType()));
        boolean restricted = zones.stream().anyMatch(z -> "RESTRICTED".equals(z.getType()));
        List<ZoneDto> zoneDtos = zones.stream().map(zoneMapper::toDto).collect(Collectors.toList());
        ZoneCheckResponse response =
                ZoneCheckResponse.builder()
                        .inServiceArea(inServiceArea)
                        .restricted(restricted)
                        .zones(zoneDtos)
                        .build();
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/cities/{cityId}/zones")
    public ResponseEntity<ApiResponse<List<ZoneDto>>> listZones(@PathVariable UUID cityId) {
        List<ZoneDto> zones =
                zoneService.listZones(cityId).stream()
                        .map(zoneMapper::toDto)
                        .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(zones));
    }

    @PostMapping("/cities/{cityId}/zones")
    public ResponseEntity<ApiResponse<ZoneDto>> createZone(
            @PathVariable UUID cityId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader("X-Country-Code") String countryCode,
            @Valid @RequestBody CreateZoneRequest request) {
        validateAdmin(role);
        Zone zone = zoneService.createZone(cityId, countryCode, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(zoneMapper.toDto(zone)));
    }

    @PutMapping("/zones/{id}")
    public ResponseEntity<ApiResponse<ZoneDto>> updateZone(
            @PathVariable UUID id,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody UpdateZoneRequest request) {
        validateAdmin(role);
        Zone zone = zoneService.updateZone(id, request);
        return ResponseEntity.ok(ApiResponse.ok(zoneMapper.toDto(zone)));
    }

    @DeleteMapping("/zones/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateZone(
            @PathVariable UUID id, @RequestHeader("X-User-Role") String role) {
        validateAdmin(role);
        zoneService.deactivateZone(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private void validateAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new UnauthorizedException("Admin access required");
        }
    }
}
