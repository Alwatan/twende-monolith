package tz.co.twende.matching.client;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tz.co.twende.common.response.ApiResponse;

@Component
@Slf4j
public class RatingServiceClient {

    private static final BigDecimal DEFAULT_RATING = new BigDecimal("4.0");

    private final RestClient restClient;

    public RatingServiceClient(@Value("${twende.services.rating-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public BigDecimal getDriverAverageRating(UUID driverId) {
        try {
            ApiResponse<BigDecimal> response =
                    restClient
                            .get()
                            .uri("/internal/ratings/driver/{driverId}/average", driverId)
                            .retrieve()
                            .body(new ParameterizedTypeReference<ApiResponse<BigDecimal>>() {});
            if (response != null && response.getData() != null) {
                return response.getData();
            }
            return DEFAULT_RATING;
        } catch (Exception e) {
            log.warn(
                    "Failed to fetch rating for driver {}, using default: {}",
                    driverId,
                    e.getMessage());
            return DEFAULT_RATING;
        }
    }
}
