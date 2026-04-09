package tz.co.twende.compliance.service;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tz.co.twende.compliance.entity.AuditLog;
import tz.co.twende.compliance.repository.AuditLogRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public void log(
            String countryCode, String eventType, UUID entityId, UUID actorId, Object payload) {
        AuditLog entry = new AuditLog();
        entry.setCountryCode(countryCode);
        entry.setEventType(eventType);
        entry.setEntityId(entityId);
        entry.setActorId(actorId);
        entry.setOccurredAt(Instant.now());

        try {
            entry.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn(
                    "Failed to serialize audit payload for event {}: {}",
                    eventType,
                    e.getMessage());
            entry.setPayload("{}");
        }

        auditLogRepository.save(entry);
        log.debug("Audit log: {} for entity {} in {}", eventType, entityId, countryCode);
    }
}
