package tz.co.twende.driver.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class RestClientConfig {
    // RestClient instances are created directly in SubscriptionClient
    // and LocationClient via @Value injection
}
