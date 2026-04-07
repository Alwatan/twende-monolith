package tz.co.twende.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient rideServiceRestClient(
            @Value("${twende.services.ride-service.url}") String rideServiceUrl) {
        return RestClient.builder()
                .baseUrl(rideServiceUrl)
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
