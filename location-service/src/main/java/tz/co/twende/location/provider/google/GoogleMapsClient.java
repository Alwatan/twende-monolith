package tz.co.twende.location.provider.google;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tz.co.twende.location.config.GoogleMapsProperties;
import tz.co.twende.location.dto.GeocodingResult;
import tz.co.twende.location.dto.PlaceResult;
import tz.co.twende.location.dto.Route;

@Component
@Slf4j
public class GoogleMapsClient {

    private final RestClient restClient;
    private final String apiKey;

    public GoogleMapsClient(GoogleMapsProperties properties) {
        this.apiKey = properties.getApiKey();
        this.restClient =
                RestClient.builder()
                        .baseUrl("https://maps.googleapis.com/maps/api")
                        .defaultHeader("Accept", "application/json")
                        .build();
    }

    public GeocodingResult geocode(String address) {
        try {
            Map<String, Object> response =
                    restClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("/geocode/json")
                                                    .queryParam("address", address)
                                                    .queryParam("key", apiKey)
                                                    .build())
                            .retrieve()
                            .body(new ParameterizedTypeReference<>() {});
            return parseGeocodingResponse(response);
        } catch (Exception e) {
            log.error("Google geocode failed for address: {}", address, e);
            throw new RuntimeException("Geocoding failed", e);
        }
    }

    public GeocodingResult reverseGeocode(double lat, double lng) {
        try {
            Map<String, Object> response =
                    restClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("/geocode/json")
                                                    .queryParam("latlng", lat + "," + lng)
                                                    .queryParam("key", apiKey)
                                                    .build())
                            .retrieve()
                            .body(new ParameterizedTypeReference<>() {});
            return parseGeocodingResponse(response);
        } catch (Exception e) {
            log.error("Google reverse geocode failed for {},{}", lat, lng, e);
            throw new RuntimeException("Reverse geocoding failed", e);
        }
    }

    public Route directions(double oLat, double oLng, double dLat, double dLng) {
        try {
            Map<String, Object> response =
                    restClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("/directions/json")
                                                    .queryParam("origin", oLat + "," + oLng)
                                                    .queryParam("destination", dLat + "," + dLng)
                                                    .queryParam("key", apiKey)
                                                    .build())
                            .retrieve()
                            .body(new ParameterizedTypeReference<>() {});
            return parseDirectionsResponse(response);
        } catch (Exception e) {
            log.error("Google directions failed", e);
            throw new RuntimeException("Directions failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<PlaceResult> autocomplete(
            String input, double lat, double lng, String countryCode) {
        try {
            Map<String, Object> response =
                    restClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("/place/autocomplete/json")
                                                    .queryParam("input", input)
                                                    .queryParam("location", lat + "," + lng)
                                                    .queryParam("radius", 50000)
                                                    .queryParam(
                                                            "components",
                                                            "country:" + countryCode.toLowerCase())
                                                    .queryParam("key", apiKey)
                                                    .build())
                            .retrieve()
                            .body(new ParameterizedTypeReference<>() {});
            return parseAutocompleteResponse(response);
        } catch (Exception e) {
            log.error("Google autocomplete failed for input: {}", input, e);
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private GeocodingResult parseGeocodingResponse(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
        if (results == null || results.isEmpty()) {
            return null;
        }
        Map<String, Object> first = results.get(0);
        Map<String, Object> geometry = (Map<String, Object>) first.get("geometry");
        Map<String, Object> location = (Map<String, Object>) geometry.get("location");

        return GeocodingResult.builder()
                .latitude(new BigDecimal(String.valueOf(location.get("lat"))))
                .longitude(new BigDecimal(String.valueOf(location.get("lng"))))
                .formattedAddress((String) first.get("formatted_address"))
                .placeId((String) first.get("place_id"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Route parseDirectionsResponse(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        List<Map<String, Object>> routes = (List<Map<String, Object>>) response.get("routes");
        if (routes == null || routes.isEmpty()) {
            return null;
        }
        Map<String, Object> route = routes.get(0);
        List<Map<String, Object>> legs = (List<Map<String, Object>>) route.get("legs");
        Map<String, Object> leg = legs.get(0);
        Map<String, Object> distance = (Map<String, Object>) leg.get("distance");
        Map<String, Object> duration = (Map<String, Object>) leg.get("duration");
        Map<String, Object> overviewPolyline = (Map<String, Object>) route.get("overview_polyline");

        return Route.builder()
                .distanceMetres(((Number) distance.get("value")).intValue())
                .durationSeconds(((Number) duration.get("value")).intValue())
                .polyline(overviewPolyline != null ? (String) overviewPolyline.get("points") : null)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<PlaceResult> parseAutocompleteResponse(Map<String, Object> response) {
        if (response == null) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> predictions =
                (List<Map<String, Object>>) response.get("predictions");
        if (predictions == null) {
            return Collections.emptyList();
        }
        return predictions.stream()
                .map(
                        p -> {
                            Map<String, Object> structured =
                                    (Map<String, Object>) p.get("structured_formatting");
                            return PlaceResult.builder()
                                    .placeId((String) p.get("place_id"))
                                    .description((String) p.get("description"))
                                    .mainText(
                                            structured != null
                                                    ? (String) structured.get("main_text")
                                                    : null)
                                    .secondaryText(
                                            structured != null
                                                    ? (String) structured.get("secondary_text")
                                                    : null)
                                    .build();
                        })
                .toList();
    }
}
