package tz.co.twende.user.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tz.co.twende.user.kafka.event.UserProfileUpdatedEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserProfileUpdatedProducer {

    public static final String TOPIC = "twende.users.profile-updated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(UserProfileUpdatedEvent event) {
        kafkaTemplate.send(TOPIC, event.getUserId().toString(), event);
        log.debug("Published UserProfileUpdatedEvent for user {}", event.getUserId());
    }
}
