package tz.co.twende.location.client;

import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tz.co.twende.location.dto.OperatingCityDto;

@Component
@Slf4j
public class CountryConfigClient {

    private final RestClient restClient;

    public CountryConfigClient(
            @Value("${twende.services.country-config-service.url}") String baseUrl) {
        this.restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("Accept", "application/json")
                        .build();
    }

    public OperatingCityDto getCity(UUID cityId) {
        try {
            Map<String, Object> response =
                    restClient
                            .get()
                            .uri("/internal/config/cities/{cityId}", cityId)
                            .retrieve()
                            .body(new ParameterizedTypeReference<>() {});
            if (response != null && response.get("data") != null) {
                return mapToDto(response.get("data"));
            }
            throw new RuntimeException("City not found: " + cityId);
        } catch (Exception e) {
            log.error("Failed to fetch city config for {}", cityId, e);
            // Fallback: return a default config with google providers
            OperatingCityDto fallback = new OperatingCityDto();
            fallback.setCityId(cityId);
            fallback.setGeocodingProvider("GOOGLE");
            fallback.setRoutingProvider("GOOGLE");
            fallback.setAutocompleteProvider("GOOGLE");
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private OperatingCityDto mapToDto(Object data) {
        Map<String, Object> map = (Map<String, Object>) data;
        OperatingCityDto dto = new OperatingCityDto();
        dto.setCityId(
                map.get("cityId") != null ? UUID.fromString(map.get("cityId").toString()) : null);
        dto.setName((String) map.get("name"));
        dto.setCountryCode((String) map.get("countryCode"));
        dto.setGeocodingProvider(
                map.get("geocodingProvider") != null
                        ? (String) map.get("geocodingProvider")
                        : "GOOGLE");
        dto.setRoutingProvider(
                map.get("routingProvider") != null
                        ? (String) map.get("routingProvider")
                        : "GOOGLE");
        dto.setAutocompleteProvider(
                map.get("autocompleteProvider") != null
                        ? (String) map.get("autocompleteProvider")
                        : "GOOGLE");
        return dto;
    }
}
