package tz.co.twende.location.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tz.co.twende.location.dto.NearbyDriverResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

    private final RedisTemplate<String, Object> redisTemplate;

    public void updateDriverLocation(
            UUID driverId,
            String countryCode,
            String vehicleType,
            BigDecimal lat,
            BigDecimal lng,
            int bearing,
            int speedKmh) {
        String geoKey = "drivers:" + countryCode + ":" + vehicleType;
        redisTemplate
                .opsForGeo()
                .add(geoKey, new Point(lng.doubleValue(), lat.doubleValue()), driverId.toString());

        String hashKey = "driver:location:" + driverId;
        redisTemplate
                .opsForHash()
                .putAll(
                        hashKey,
                        Map.of(
                                "lat", lat.toPlainString(),
                                "lng", lng.toPlainString(),
                                "bearing", String.valueOf(bearing),
                                "speed", String.valueOf(speedKmh),
                                "updatedAt", java.time.Instant.now().toString()));
        redisTemplate.expire(hashKey, 90, TimeUnit.SECONDS);
    }

    public void removeDriverFromGeoIndex(UUID driverId, String countryCode, String vehicleType) {
        String geoKey = "drivers:" + countryCode + ":" + vehicleType;
        redisTemplate.opsForGeo().remove(geoKey, driverId.toString());
        redisTemplate.delete("driver:location:" + driverId);
        log.debug("Removed driver {} from GEO index {}", driverId, geoKey);
    }

    public List<NearbyDriverResponse> findNearbyDrivers(
            String countryCode,
            String vehicleType,
            BigDecimal lat,
            BigDecimal lng,
            BigDecimal radiusKm) {
        String geoKey = "drivers:" + countryCode + ":" + vehicleType;
        GeoResults<RedisGeoCommands.GeoLocation<Object>> results =
                redisTemplate
                        .opsForGeo()
                        .radius(
                                geoKey,
                                new Circle(
                                        new Point(lng.doubleValue(), lat.doubleValue()),
                                        new Distance(radiusKm.doubleValue(), Metrics.KILOMETERS)),
                                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                        .includeDistance()
                                        .includeCoordinates()
                                        .sortAscending());

        if (results == null) {
            return Collections.emptyList();
        }

        List<NearbyDriverResponse> drivers = new ArrayList<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<Object>> result : results) {
            UUID driverId;
            try {
                driverId = UUID.fromString(result.getContent().getName().toString());
            } catch (IllegalArgumentException e) {
                continue;
            }
            Point point = result.getContent().getPoint();
            Map<Object, Object> details =
                    redisTemplate.opsForHash().entries("driver:location:" + driverId);

            NearbyDriverResponse response =
                    NearbyDriverResponse.builder()
                            .driverId(driverId)
                            .latitude(BigDecimal.valueOf(point.getY()))
                            .longitude(BigDecimal.valueOf(point.getX()))
                            .distanceKm(BigDecimal.valueOf(result.getDistance().getValue()))
                            .bearing(
                                    details.containsKey("bearing")
                                            ? Integer.parseInt((String) details.get("bearing"))
                                            : 0)
                            .speedKmh(
                                    details.containsKey("speed")
                                            ? Integer.parseInt((String) details.get("speed"))
                                            : 0)
                            .build();
            drivers.add(response);
        }
        return drivers;
    }

    public NearbyDriverResponse getDriverLocation(UUID driverId) {
        Map<Object, Object> details =
                redisTemplate.opsForHash().entries("driver:location:" + driverId);
        if (details.isEmpty()) {
            return null;
        }
        return NearbyDriverResponse.builder()
                .driverId(driverId)
                .latitude(new BigDecimal((String) details.get("lat")))
                .longitude(new BigDecimal((String) details.get("lng")))
                .bearing(
                        details.containsKey("bearing")
                                ? Integer.parseInt((String) details.get("bearing"))
                                : 0)
                .speedKmh(
                        details.containsKey("speed")
                                ? Integer.parseInt((String) details.get("speed"))
                                : 0)
                .build();
    }

    public void appendToTripTrace(UUID rideId, BigDecimal lat, BigDecimal lng) {
        String key = "ride:trace:" + rideId;
        String point =
                "{\"lat\":"
                        + lat.toPlainString()
                        + ",\"lng\":"
                        + lng.toPlainString()
                        + ",\"ts\":\""
                        + java.time.Instant.now()
                        + "\"}";
        redisTemplate.opsForList().rightPush(key, point);
        redisTemplate.expire(key, 48, TimeUnit.HOURS);
    }
}
