package tz.co.twende.matching.client;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class RideServiceClient {

    private final RestClient restClient;

    public RideServiceClient(@Value("${twende.services.ride-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void notifyOfferAccepted(UUID rideId, UUID driverId) {
        try {
            restClient
                    .post()
                    .uri("/internal/rides/{rideId}/offer-accepted", rideId)
                    .body(java.util.Map.of("driverId", driverId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error(
                    "Failed to notify ride-service of acceptance for ride {}: {}",
                    rideId,
                    e.getMessage());
        }
    }
}
