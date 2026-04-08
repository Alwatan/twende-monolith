package tz.co.twende.matching.client;

import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tz.co.twende.common.response.ApiResponse;

@Component
@Slf4j
public class DriverServiceClient {

    private final RestClient restClient;

    public DriverServiceClient(@Value("${twende.services.driver-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getDriver(UUID driverId) {
        try {
            ApiResponse<Map<String, Object>> response =
                    restClient
                            .get()
                            .uri("/internal/drivers/{id}", driverId)
                            .retrieve()
                            .body(
                                    new ParameterizedTypeReference<
                                            ApiResponse<Map<String, Object>>>() {});
            if (response != null) {
                return response.getData();
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch driver {}: {}", driverId, e.getMessage());
            return null;
        }
    }
}
