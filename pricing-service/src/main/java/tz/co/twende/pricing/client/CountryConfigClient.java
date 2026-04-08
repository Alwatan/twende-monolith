package tz.co.twende.pricing.client;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tz.co.twende.pricing.dto.VehicleTypeConfigDto;

@Component
public class CountryConfigClient {

    private static final Logger log = LoggerFactory.getLogger(CountryConfigClient.class);
    private static final String CACHE_PREFIX = "pricing:vehicle-config:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final RestClient restClient;
    private final RedisTemplate<String, Object> redisTemplate;

    public CountryConfigClient(
            @Value("${twende.services.country-config-service.url}") String baseUrl,
            RedisTemplate<String, Object> redisTemplate) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.redisTemplate = redisTemplate;
    }

    public VehicleTypeConfigDto getVehicleTypeConfig(String countryCode, String vehicleType) {
        String cacheKey = CACHE_PREFIX + countryCode + ":" + vehicleType;

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof VehicleTypeConfigDto dto) {
            return dto;
        }

        log.debug(
                "Fetching vehicle type config from country-config-service: {}/{}",
                countryCode,
                vehicleType);

        VehicleTypeConfigDto config =
                restClient
                        .get()
                        .uri(
                                "/api/v1/config/{countryCode}/vehicle-types/{vehicleType}",
                                countryCode,
                                vehicleType)
                        .retrieve()
                        .body(VehicleTypeConfigDto.class);

        if (config != null) {
            redisTemplate.opsForValue().set(cacheKey, config, CACHE_TTL);
        }
        return config;
    }

    public void evictCache(String countryCode) {
        log.info("Evicting pricing config cache for country: {}", countryCode);
        var keys = redisTemplate.keys(CACHE_PREFIX + countryCode + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
