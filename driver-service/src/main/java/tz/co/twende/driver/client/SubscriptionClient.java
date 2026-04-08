package tz.co.twende.driver.client;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.driver.dto.response.RevenueModelResponseDto;

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
            ApiResponse<Boolean> response =
                    restClient
                            .get()
                            .uri("/internal/subscriptions/{driverId}/active", driverId)
                            .retrieve()
                            .onStatus(
                                    HttpStatusCode::is4xxClientError,
                                    (request, resp) -> {
                                        // 404 means no active subscription
                                    })
                            .body(new ParameterizedTypeReference<ApiResponse<Boolean>>() {});
            return response != null && response.getData() != null && response.getData();
        } catch (Exception e) {
            log.debug("No active subscription found for driver {}: {}", driverId, e.getMessage());
            return false;
        }
    }

    public RevenueModelResponseDto getRevenueModel(UUID driverId) {
        try {
            ApiResponse<RevenueModelResponseDto> response =
                    restClient
                            .get()
                            .uri("/internal/subscriptions/{driverId}/revenue-model", driverId)
                            .retrieve()
                            .body(
                                    new ParameterizedTypeReference<
                                            ApiResponse<RevenueModelResponseDto>>() {});
            return response != null ? response.getData() : null;
        } catch (Exception e) {
            log.debug("Failed to get revenue model for driver {}: {}", driverId, e.getMessage());
            return null;
        }
    }
}
