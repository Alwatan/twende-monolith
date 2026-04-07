package tz.co.twende.driver.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_USER_REGISTERED = "twende.users.registered";
    public static final String TOPIC_RIDES_COMPLETED = "twende.rides.completed";
    public static final String TOPIC_SUBSCRIPTIONS_ACTIVATED =
            "twende.subscriptions.activated";
    public static final String TOPIC_SUBSCRIPTIONS_EXPIRED =
            "twende.subscriptions.expired";

    public static final String TOPIC_DRIVERS_STATUS_UPDATED =
            "twende.drivers.status-updated";
    public static final String TOPIC_DRIVERS_APPROVED = "twende.drivers.approved";
}
