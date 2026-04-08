package tz.co.twende.rating.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_RIDES_COMPLETED = "twende.rides.completed";
    public static final String TOPIC_RATINGS_SUBMITTED = "twende.ratings.submitted";
}
