package tz.co.twende.pricing.client;

import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tz.co.twende.pricing.dto.RouteDto;
import tz.co.twende.pricing.dto.ZoneCheckDto;

@Component
public class LocationServiceClient {

    private static final Logger log = LoggerFactory.getLogger(LocationServiceClient.class);

    private final RestClient restClient;

    public LocationServiceClient(@Value("${twende.services.location-service.url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public RouteDto getRoute(
            BigDecimal originLat,
            BigDecimal originLng,
            BigDecimal destLat,
            BigDecimal destLng,
            UUID cityId) {
        log.debug("Fetching route from location-service for cityId: {}", cityId);

        return restClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/internal/location/route")
                                        .queryParam("originLat", originLat)
                                        .queryParam("originLng", originLng)
                                        .queryParam("destLat", destLat)
                                        .queryParam("destLng", destLng)
                                        .queryParam("cityId", cityId)
                                        .build())
                .retrieve()
                .body(RouteDto.class);
    }

    public ZoneCheckDto checkZones(BigDecimal lat, BigDecimal lng, UUID cityId) {
        log.debug("Checking zones from location-service at ({}, {}) cityId: {}", lat, lng, cityId);

        return restClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/internal/location/zones/check")
                                        .queryParam("lat", lat)
                                        .queryParam("lng", lng)
                                        .queryParam("cityId", cityId)
                                        .build())
                .retrieve()
                .body(ZoneCheckDto.class);
    }
}
