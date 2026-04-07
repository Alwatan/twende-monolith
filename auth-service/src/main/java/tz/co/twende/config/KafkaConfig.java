package tz.co.twende.auth.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_USER_REGISTERED = "twende.users.registered";
}
