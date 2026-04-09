package tz.co.twende.compliance.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDto {
    private UUID id;
    private String countryCode;
    private String eventType;
    private UUID entityId;
    private UUID actorId;
    private String payload;
    private Instant occurredAt;
}
