package tz.co.twende.rating.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.rating.RatingSubmittedEvent;
import tz.co.twende.rating.entity.Rating;

@Component
@RequiredArgsConstructor
@Slf4j
public class RatingEventPublisher {

    private static final String TOPIC_RATINGS_SUBMITTED = "twende.ratings.submitted";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishRatingSubmitted(Rating rating) {
        RatingSubmittedEvent event = new RatingSubmittedEvent();
        event.setEventType("RATING_SUBMITTED");
        event.setCountryCode(rating.getCountryCode());
        event.setRideId(rating.getRideId());
        event.setRatedUserId(rating.getRatedUserId());
        event.setRaterUserId(rating.getRaterUserId());
        event.setRaterRole(rating.getRaterRole());
        event.setScore(rating.getScore().shortValue());

        String key = rating.getCountryCode() + ":" + rating.getRideId();
        kafkaTemplate.send(TOPIC_RATINGS_SUBMITTED, key, event);
        log.debug(
                "Published RatingSubmittedEvent for ride {} by {}",
                rating.getRideId(),
                rating.getRaterRole());
    }
}
