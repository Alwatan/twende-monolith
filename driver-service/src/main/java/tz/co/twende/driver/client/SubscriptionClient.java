package tz.co.twende.driver.client;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class SubscriptionClient {

    private final RestClient restClient;

    public SubscriptionClient(
            @Value("${twende.services.subscription-service.url}") String baseUrl) {
        this.restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("Accept", "application/json")
                        .build();
    }

    public boolean hasActiveSubscription(UUID driverId) {
        try {
            restClient
                    .get()
                    .uri("/internal/subscriptions/{driverId}/active", driverId)
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::is4xxClientError,
                            (request, response) -> {
                                // 404 means no active subscription
                            })
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.debug(
                    "No active subscription found for driver {}: {}",
                    driverId,
                    e.getMessage());
            return false;
        }
    }
}
