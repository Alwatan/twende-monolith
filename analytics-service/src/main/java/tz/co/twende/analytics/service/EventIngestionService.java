package tz.co.twende.analytics.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import tz.co.twende.analytics.entity.AnalyticsEvent;
import tz.co.twende.analytics.repository.AnalyticsEventRepository;
import tz.co.twende.common.event.KafkaEvent;
import tz.co.twende.common.event.driver.DriverStatusUpdatedEvent;
import tz.co.twende.common.event.payment.PaymentCompletedEvent;
import tz.co.twende.common.event.rating.RatingSubmittedEvent;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.common.event.subscription.SubscriptionActivatedEvent;
import tz.co.twende.common.event.user.UserRegisteredEvent;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventIngestionService {

    private final AnalyticsEventRepository eventRepository;
    private final JsonMapper jsonMapper;

    public void ingest(KafkaEvent event) {
        AnalyticsEvent entity = new AnalyticsEvent();
        entity.setEventType(event.getEventType());
        entity.setCountryCode(event.getCountryCode() != null ? event.getCountryCode() : "XX");
        entity.setActorId(resolveActorId(event));
        entity.setPayload(serializePayload(event));
        entity.setOccurredAt(
                event.getTimestamp() != null ? event.getTimestamp() : java.time.Instant.now());
        eventRepository.save(entity);
        log.debug(
                "Ingested analytics event: type={}, actorId={}",
                entity.getEventType(),
                entity.getActorId());
    }

    UUID resolveActorId(KafkaEvent event) {
        if (event instanceof RideCompletedEvent rce) {
            return rce.getDriverId();
        }
        if (event instanceof PaymentCompletedEvent pce) {
            return pce.getUserId();
        }
        if (event instanceof SubscriptionActivatedEvent sae) {
            return sae.getDriverId();
        }
        if (event instanceof UserRegisteredEvent ure) {
            return ure.getUserId();
        }
        if (event instanceof RatingSubmittedEvent rse) {
            return rse.getRaterUserId();
        }
        if (event instanceof DriverStatusUpdatedEvent dsu) {
            return dsu.getDriverId();
        }
        return null;
    }

    private String serializePayload(KafkaEvent event) {
        try {
            return jsonMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.warn("Failed to serialize event payload, using toString", e);
            return "{}";
        }
    }
}
