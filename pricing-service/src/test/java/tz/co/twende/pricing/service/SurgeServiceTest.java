package tz.co.twende.pricing.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tz.co.twende.pricing.dto.SurgeResponse;

@ExtendWith(MockitoExtension.class)
class SurgeServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;

    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks private SurgeService surgeService;

    @Test
    void givenCachedSurge_whenGetSurge_thenReturnsCachedValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("surge:TZ:BAJAJ")).thenReturn(new BigDecimal("1.5"));

        BigDecimal surge = surgeService.getSurge("TZ", "BAJAJ");

        assertThat(surge).isEqualByComparingTo(new BigDecimal("1.5"));
    }

    @Test
    void givenNoCachedSurge_whenGetSurge_thenReturnsDefault() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("surge:TZ:BAJAJ")).thenReturn(null);

        BigDecimal surge = surgeService.getSurge("TZ", "BAJAJ");

        assertThat(surge).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void givenNumericCachedValue_whenGetSurge_thenConvertsToDecimal() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("surge:TZ:BODA_BODA")).thenReturn(1.8);

        BigDecimal surge = surgeService.getSurge("TZ", "BODA_BODA");

        assertThat(surge).isEqualByComparingTo(new BigDecimal("1.8"));
    }

    @Test
    void givenSurge_whenGetSurgeResponse_thenReturnsFullResponse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("surge:TZ:BAJAJ")).thenReturn(new BigDecimal("1.3"));

        SurgeResponse response = surgeService.getSurgeResponse("TZ", "BAJAJ");

        assertThat(response.getVehicleType()).isEqualTo("BAJAJ");
        assertThat(response.getCountryCode()).isEqualTo("TZ");
        assertThat(response.getSurgeMultiplier()).isEqualByComparingTo(new BigDecimal("1.3"));
        assertThat(response.getUpdatedAt()).isNotNull();
    }

    @Test
    void givenSetSurge_whenCalled_thenStoresInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        surgeService.setSurge("TZ", "BAJAJ", new BigDecimal("2.0"));

        verify(valueOperations).set(eq("surge:TZ:BAJAJ"), eq(new BigDecimal("2.0")), any());
    }
}
