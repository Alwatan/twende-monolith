package tz.co.twende.rating.kafka;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tz.co.twende.common.event.rating.RatingSubmittedEvent;
import tz.co.twende.rating.entity.Rating;

@ExtendWith(MockitoExtension.class)
class RatingEventPublisherTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private RatingEventPublisher publisher;

    @Test
    void givenRating_whenPublish_thenSendsEventWithCorrectKey() {
        Rating rating = new Rating();
        rating.setRideId(UUID.randomUUID());
        rating.setRatedUserId(UUID.randomUUID());
        rating.setRaterUserId(UUID.randomUUID());
        rating.setRaterRole("RIDER");
        rating.setScore((short) 5);
        rating.setCountryCode("TZ");

        publisher.publishRatingSubmitted(rating);

        String expectedKey = "TZ:" + rating.getRideId();
        verify(kafkaTemplate)
                .send(
                        eq("twende.ratings.submitted"),
                        eq(expectedKey),
                        any(RatingSubmittedEvent.class));
    }
}
