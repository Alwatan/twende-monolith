package tz.co.twende.payment.client;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.payment.dto.response.RevenueModelDto;

@Component
@Slf4j
public class SubscriptionServiceClient {

    private final RestClient restClient;

    public SubscriptionServiceClient(
            @Value("${twende.services.subscription-service.url}") String baseUrl) {
        this.restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("Accept", "application/json")
                        .build();
    }

    public RevenueModelDto getRevenueModel(UUID driverId) {
        try {
            ApiResponse<RevenueModelDto> response =
                    restClient
                            .get()
                            .uri("/internal/subscriptions/{driverId}/revenue-model", driverId)
                            .retrieve()
                            .body(
                                    new ParameterizedTypeReference<
                                            ApiResponse<RevenueModelDto>>() {});
            return response != null ? response.getData() : null;
        } catch (Exception e) {
            log.warn("Failed to get revenue model for driver {}: {}", driverId, e.getMessage());
            return null;
        }
    }
}
