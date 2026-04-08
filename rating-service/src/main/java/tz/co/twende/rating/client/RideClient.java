package tz.co.twende.rating.client;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tz.co.twende.rating.dto.RideDetailsDto;

@Component
public class RideClient {

    private final RestClient restClient;

    public RideClient(@Value("${twende.services.ride-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public RideDetailsDto getRide(UUID rideId) {
        return restClient
                .get()
                .uri("/internal/rides/{id}", rideId)
                .retrieve()
                .body(RideDetailsDto.class);
    }
}
