package tz.co.twende.ride.client;

import java.math.BigDecimal;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class LocationClient {

    private final RestClient restClient;

    public LocationClient(@Value("${twende.services.location-service.url}") String baseUrl) {
        this.restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("Accept", "application/json")
                        .build();
    }

    /**
     * Check if a point is in a restricted zone. Returns true if the pickup location is in a
     * restricted zone (ride should be rejected).
     */
    public boolean isInRestrictedZone(BigDecimal lat, BigDecimal lng) {
        try {
            Map<String, Object> result =
                    restClient
                            .get()
                            .uri("/internal/locations/geofence/check?lat={lat}&lng={lng}", lat, lng)
                            .retrieve()
                            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (result != null && result.containsKey("restricted")) {
                return Boolean.TRUE.equals(result.get("restricted"));
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to check geofence: {}", e.getMessage());
            return false;
        }
    }
}
