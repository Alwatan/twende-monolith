package com.twende.common.event;

import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class KafkaEvent {
    private String eventId = UUID.randomUUID().toString();
    private String eventType;
    private String countryCode;
    private Instant timestamp = Instant.now();
    private String correlationId;
}
