package tz.co.twende.analytics.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tz.co.twende.analytics.config.KafkaConfig;
import tz.co.twende.analytics.service.EventIngestionService;
import tz.co.twende.common.event.KafkaEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsEventConsumer {

    private final EventIngestionService eventIngestionService;

    @KafkaListener(
            topics = {
                KafkaConfig.TOPIC_RIDES_COMPLETED,
                KafkaConfig.TOPIC_RIDES_CANCELLED,
                KafkaConfig.TOPIC_PAYMENTS_COMPLETED,
                KafkaConfig.TOPIC_SUBSCRIPTIONS_ACTIVATED,
                KafkaConfig.TOPIC_USERS_REGISTERED,
                KafkaConfig.TOPIC_DRIVERS_APPROVED,
                KafkaConfig.TOPIC_RATINGS_SUBMITTED
            },
            groupId = "${spring.kafka.consumer.group-id}")
    public void onEvent(KafkaEvent event) {
        log.debug("Received event: type={}", event.getEventType());
        try {
            eventIngestionService.ingest(event);
        } catch (Exception e) {
            log.error(
                    "Failed to ingest analytics event: type={}, error={}",
                    event.getEventType(),
                    e.getMessage(),
                    e);
        }
    }
}
