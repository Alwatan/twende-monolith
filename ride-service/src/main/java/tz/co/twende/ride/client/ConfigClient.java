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
public class ConfigClient {

    private final RestClient restClient;

    public ConfigClient(@Value("${twende.services.country-config-service.url}") String baseUrl) {
        this.restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("Accept", "application/json")
                        .build();
    }

    /**
     * Get the maximum fare cap for a vehicle type in a country. Returns baseFare *
     * surgeMultiplierCap.
     */
    public BigDecimal getMaxFareCap(String countryCode, String vehicleType) {
        try {
            Map<String, Object> result =
                    restClient
                            .get()
                            .uri(
                                    "/internal/config/vehicle-types/{country}/{type}",
                                    countryCode,
                                    vehicleType)
                            .retrieve()
                            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (result != null) {
                BigDecimal baseFare =
                        new BigDecimal(String.valueOf(result.getOrDefault("baseFare", "0")));
                BigDecimal surgeMultiplierCap =
                        new BigDecimal(
                                String.valueOf(result.getOrDefault("surgeMultiplierCap", "5")));
                return baseFare.multiply(surgeMultiplierCap);
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to get vehicle type config: {}", e.getMessage());
            return null;
        }
    }
}
