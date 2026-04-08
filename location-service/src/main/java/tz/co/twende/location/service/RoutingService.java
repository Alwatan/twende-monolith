package tz.co.twende.location.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tz.co.twende.location.dto.LatLng;
import tz.co.twende.location.dto.Route;
import tz.co.twende.location.provider.ProviderFactory;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoutingService {

    private final ProviderFactory providerFactory;
    private final RedisTemplate<String, Object> redisTemplate;

    public Route getRoute(LatLng origin, LatLng destination, UUID cityId) {
        String cacheKey = buildCacheKey(origin, destination);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof Route route) {
            return route;
        }

        var provider = providerFactory.routingFor(cityId);
        Route route = provider.getRoute(origin, destination);
        if (route != null) {
            redisTemplate.opsForValue().set(cacheKey, route, 1, TimeUnit.HOURS);
        }
        return route;
    }

    public int getEtaMinutes(LatLng origin, LatLng destination, UUID cityId) {
        Route route = getRoute(origin, destination, cityId);
        if (route == null) {
            return -1;
        }
        return (int) Math.ceil(route.getDurationSeconds() / 60.0);
    }

    private String buildCacheKey(LatLng origin, LatLng destination) {
        return "route:"
                + round(origin.getLatitude())
                + ","
                + round(origin.getLongitude())
                + ":"
                + round(destination.getLatitude())
                + ","
                + round(destination.getLongitude());
    }

    private String round(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP).toPlainString();
    }
}
