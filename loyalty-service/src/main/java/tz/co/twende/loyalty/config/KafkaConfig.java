package tz.co.twende.loyalty.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_RIDES_COMPLETED = "twende.rides.completed";
    public static final String TOPIC_FREE_RIDE_EARNED = "twende.loyalty.free-ride-earned";
}
