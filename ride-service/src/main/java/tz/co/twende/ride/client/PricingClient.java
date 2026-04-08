package tz.co.twende.ride.client;

import java.math.BigDecimal;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tz.co.twende.ride.dto.response.EstimateDto;

@Component
@Slf4j
public class PricingClient {

    private final RestClient restClient;

    public PricingClient(@Value("${twende.services.pricing-service.url}") String baseUrl) {
        this.restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("Accept", "application/json")
                        .build();
    }

    public EstimateDto getFareEstimate(
            String countryCode,
            String vehicleType,
            BigDecimal pickupLat,
            BigDecimal pickupLng,
            BigDecimal dropoffLat,
            BigDecimal dropoffLng) {
        try {
            Map<String, Object> request =
                    Map.of(
                            "countryCode", countryCode,
                            "vehicleType", vehicleType,
                            "pickupLat", pickupLat,
                            "pickupLng", pickupLng,
                            "dropoffLat", dropoffLat,
                            "dropoffLng", dropoffLng);

            return restClient
                    .post()
                    .uri("/internal/pricing/estimate")
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<EstimateDto>() {});
        } catch (Exception e) {
            log.warn("Failed to get fare estimate from pricing-service: {}", e.getMessage());
            return null;
        }
    }

    public EstimateDto calculateFinalFare(
            String countryCode,
            String vehicleType,
            Integer distanceMetres,
            Integer durationSeconds) {
        try {
            Map<String, Object> request =
                    Map.of(
                            "countryCode",
                            countryCode,
                            "vehicleType",
                            vehicleType,
                            "distanceMetres",
                            distanceMetres != null ? distanceMetres : 0,
                            "durationSeconds",
                            durationSeconds != null ? durationSeconds : 0);

            return restClient
                    .post()
                    .uri("/internal/pricing/calculate")
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<EstimateDto>() {});
        } catch (Exception e) {
            log.warn("Failed to calculate final fare: {}", e.getMessage());
            return null;
        }
    }
}
