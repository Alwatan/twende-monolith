package tz.co.twende.driver.client;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tz.co.twende.common.enums.VehicleType;

@Component
@Slf4j
public class LocationClient {

    private final RestClient restClient;

    public LocationClient(
            @Value("${twende.services.location-service.url}") String baseUrl) {
        this.restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("Accept", "application/json")
                        .build();
    }

    public void addDriverToGeoIndex(
            UUID driverId, String countryCode, VehicleType vehicleType) {
        try {
            restClient
                    .post()
                    .uri(
                            "/internal/locations/drivers/{driverId}/index"
                                    + "?countryCode={cc}&vehicleType={vt}",
                            driverId,
                            countryCode,
                            vehicleType)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn(
                    "Failed to add driver {} to GEO index: {}",
                    driverId,
                    e.getMessage());
        }
    }

    public void removeDriverFromGeoIndex(UUID driverId, String countryCode) {
        try {
            restClient
                    .delete()
                    .uri(
                            "/internal/locations/drivers/{driverId}/index"
                                    + "?countryCode={cc}",
                            driverId,
                            countryCode)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn(
                    "Failed to remove driver {} from GEO index: {}",
                    driverId,
                    e.getMessage());
        }
    }
}
