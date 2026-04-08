package tz.co.twende.pricing.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tz.co.twende.pricing.dto.SurgeResponse;

@Service
public class SurgeService {

    private static final Logger log = LoggerFactory.getLogger(SurgeService.class);
    private static final String SURGE_KEY_PREFIX = "surge:";
    private static final Duration SURGE_TTL = Duration.ofSeconds(120);
    private static final BigDecimal DEFAULT_SURGE = BigDecimal.ONE;

    private final RedisTemplate<String, Object> redisTemplate;

    public SurgeService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public BigDecimal getSurge(String countryCode, String vehicleType) {
        String key = buildKey(countryCode, vehicleType);
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof BigDecimal surge) {
            return surge;
        }
        if (cached instanceof Number num) {
            return BigDecimal.valueOf(num.doubleValue());
        }
        return DEFAULT_SURGE;
    }

    public SurgeResponse getSurgeResponse(String countryCode, String vehicleType) {
        BigDecimal surge = getSurge(countryCode, vehicleType);
        return SurgeResponse.builder()
                .vehicleType(vehicleType)
                .countryCode(countryCode)
                .surgeMultiplier(surge)
                .updatedAt(Instant.now())
                .build();
    }

    @Scheduled(fixedRate = 60000)
    public void updateSurgeMultipliers() {
        log.debug("Running surge multiplier update (placeholder: setting 1.0 for all)");
        // Phase 3 implementation: For each active country + vehicleType:
        // 1. Count active ride requests in last 5 minutes
        // 2. Count available drivers from location-service
        // 3. surge = min(activeRequests / availableDrivers, surgeMultiplierCap)
        // For now, just ensure infrastructure is in place with default 1.0
    }

    public void setSurge(String countryCode, String vehicleType, BigDecimal multiplier) {
        String key = buildKey(countryCode, vehicleType);
        redisTemplate.opsForValue().set(key, multiplier, SURGE_TTL);
        log.debug("Set surge for {}/{} = {}", countryCode, vehicleType, multiplier);
    }

    private String buildKey(String countryCode, String vehicleType) {
        return SURGE_KEY_PREFIX + countryCode + ":" + vehicleType;
    }
}
