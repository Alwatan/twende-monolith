package tz.co.twende.analytics.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_RIDES_COMPLETED = "twende.rides.completed";
    public static final String TOPIC_RIDES_CANCELLED = "twende.rides.cancelled";
    public static final String TOPIC_PAYMENTS_COMPLETED = "twende.payments.completed";
    public static final String TOPIC_SUBSCRIPTIONS_ACTIVATED = "twende.subscriptions.activated";
    public static final String TOPIC_USERS_REGISTERED = "twende.users.registered";
    public static final String TOPIC_DRIVERS_APPROVED = "twende.drivers.approved";
    public static final String TOPIC_RATINGS_SUBMITTED = "twende.ratings.submitted";
}
