package tz.co.twende.user.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_USER_REGISTERED = "twende.users.registered";
    public static final String TOPIC_USER_PROFILE_UPDATED = "twende.users.profile-updated";
    public static final String TOPIC_RIDES_COMPLETED = "twende.rides.completed";
}
