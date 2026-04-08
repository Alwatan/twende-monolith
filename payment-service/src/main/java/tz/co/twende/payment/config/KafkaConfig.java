package tz.co.twende.payment.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_RIDES_COMPLETED = "twende.rides.completed";
    public static final String TOPIC_SUBSCRIPTIONS_ACTIVATED = "twende.subscriptions.activated";

    public static final String TOPIC_PAYMENTS_COMPLETED = "twende.payments.completed";
    public static final String TOPIC_PAYMENTS_FAILED = "twende.payments.failed";
}
