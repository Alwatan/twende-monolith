package tz.co.twende.user.kafka;

import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tz.co.twende.user.kafka.event.UserProfileUpdatedEvent;

@ExtendWith(MockitoExtension.class)
class UserProfileUpdatedProducerTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks private UserProfileUpdatedProducer producer;

    @Test
    void givenEvent_whenPublish_thenSendsToCorrectTopic() {
        UUID userId = UUID.randomUUID();
        UserProfileUpdatedEvent event =
                UserProfileUpdatedEvent.builder()
                        .userId(userId)
                        .fullName("Jane Updated")
                        .email("jane@example.com")
                        .countryCode("TZ")
                        .timestamp(Instant.now())
                        .build();

        producer.send(event);

        verify(kafkaTemplate)
                .send(eq(UserProfileUpdatedProducer.TOPIC), eq(userId.toString()), eq(event));
        verifyNoMoreInteractions(kafkaTemplate);
    }

    @Test
    void givenEventWithNullEmail_whenPublish_thenStillSendsSuccessfully() {
        UUID userId = UUID.randomUUID();
        UserProfileUpdatedEvent event =
                UserProfileUpdatedEvent.builder()
                        .userId(userId)
                        .fullName("Jane Doe")
                        .email(null)
                        .countryCode("TZ")
                        .timestamp(Instant.now())
                        .build();

        producer.send(event);

        verify(kafkaTemplate)
                .send(eq("twende.users.profile-updated"), eq(userId.toString()), eq(event));
    }
}
