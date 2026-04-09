package tz.co.twende.compliance.controller;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.common.response.PagedResponse;
import tz.co.twende.compliance.dto.AuditLogDto;
import tz.co.twende.compliance.entity.AuditLog;
import tz.co.twende.compliance.repository.AuditLogRepository;

@RestController
@RequestMapping("/api/v1/compliance/audit-log")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogDto>>> getAuditLog(
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        Page<AuditLog> logs =
                auditLogRepository.findWithFilters(
                        countryCode,
                        eventType,
                        entityId,
                        from,
                        to,
                        PageRequest.of(page, Math.min(size, 100)));
        Page<AuditLogDto> dtoPage = logs.map(this::toDto);
        return ResponseEntity.ok(ApiResponse.ok(PagedResponse.from(dtoPage)));
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new UnauthorizedException("Admin role required");
        }
    }

    private AuditLogDto toDto(AuditLog a) {
        return AuditLogDto.builder()
                .id(a.getId())
                .countryCode(a.getCountryCode())
                .eventType(a.getEventType())
                .entityId(a.getEntityId())
                .actorId(a.getActorId())
                .payload(a.getPayload())
                .occurredAt(a.getOccurredAt())
                .build();
    }
}
