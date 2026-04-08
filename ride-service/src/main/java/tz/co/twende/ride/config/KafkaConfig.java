package tz.co.twende.ride.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_RIDES_REQUESTED = "twende.rides.requested";
    public static final String TOPIC_RIDES_STATUS_UPDATED = "twende.rides.status-updated";
    public static final String TOPIC_RIDES_COMPLETED = "twende.rides.completed";
    public static final String TOPIC_RIDES_CANCELLED = "twende.rides.cancelled";
    public static final String TOPIC_RIDES_FARE_BOOSTED = "twende.rides.fare-boosted";

    public static final String TOPIC_OFFER_ACCEPTED = "twende.rides.offer-accepted";
    public static final String TOPIC_DRIVER_REJECTED = "twende.drivers.rejected-ride";

    public static final String TOPIC_NOTIFICATIONS_SEND = "twende.notifications.send";
}
