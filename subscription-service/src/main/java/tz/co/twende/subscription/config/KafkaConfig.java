package tz.co.twende.subscription.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_SUBSCRIPTIONS_ACTIVATED = "twende.subscriptions.activated";
    public static final String TOPIC_SUBSCRIPTIONS_EXPIRED = "twende.subscriptions.expired";
}
