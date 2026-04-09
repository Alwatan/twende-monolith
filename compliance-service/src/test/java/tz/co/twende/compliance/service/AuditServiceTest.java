package tz.co.twende.compliance.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tz.co.twende.compliance.entity.AuditLog;
import tz.co.twende.compliance.repository.AuditLogRepository;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private AuditService auditService;

    @Test
    void givenValidPayload_whenLog_thenAuditLogSaved() throws Exception {
        UUID entityId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Map<String, String> payload = Map.of("key", "value");

        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"key\":\"value\"}");

        auditService.log("TZ", "RIDE_COMPLETED", entityId, actorId, payload);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("TZ", saved.getCountryCode());
        assertEquals("RIDE_COMPLETED", saved.getEventType());
        assertEquals(entityId, saved.getEntityId());
        assertEquals(actorId, saved.getActorId());
        assertEquals("{\"key\":\"value\"}", saved.getPayload());
        assertNotNull(saved.getOccurredAt());
    }

    @Test
    void givenSerializationFailure_whenLog_thenSavesEmptyPayload() throws Exception {
        UUID entityId = UUID.randomUUID();
        Object badPayload = new Object();

        when(objectMapper.writeValueAsString(badPayload))
                .thenThrow(new RuntimeException("serialization error"));

        auditService.log("TZ", "TEST_EVENT", entityId, null, badPayload);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("{}", saved.getPayload());
    }

    @Test
    void givenNullActorId_whenLog_thenSavedWithNullActor() throws Exception {
        UUID entityId = UUID.randomUUID();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        auditService.log("KE", "USER_REGISTERED", entityId, null, Map.of());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertNull(captor.getValue().getActorId());
        assertEquals("KE", captor.getValue().getCountryCode());
    }
}
