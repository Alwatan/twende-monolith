package tz.co.twende.matching.client;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.matching.dto.NearbyDriverDto;

@Component
@Slf4j
public class LocationServiceClient {

    private final RestClient restClient;

    public LocationServiceClient(@Value("${twende.services.location-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<NearbyDriverDto> findNearbyDrivers(
            String countryCode,
            String vehicleType,
            BigDecimal lat,
            BigDecimal lng,
            double radiusKm) {
        try {
            ApiResponse<List<NearbyDriverDto>> response =
                    restClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("/internal/location/drivers/nearby")
                                                    .queryParam("countryCode", countryCode)
                                                    .queryParam("vehicleType", vehicleType)
                                                    .queryParam("lat", lat)
                                                    .queryParam("lng", lng)
                                                    .queryParam("radiusKm", radiusKm)
                                                    .build())
                            .retrieve()
                            .body(
                                    new ParameterizedTypeReference<
                                            ApiResponse<List<NearbyDriverDto>>>() {});
            if (response != null && response.getData() != null) {
                return response.getData();
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch nearby drivers from location-service: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
