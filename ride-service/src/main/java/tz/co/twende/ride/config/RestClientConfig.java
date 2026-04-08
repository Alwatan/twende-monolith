package tz.co.twende.ride.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class RestClientConfig {
    // RestClient instances are created directly in each client component
    // via @Value injection of base URLs from application.yml
}
