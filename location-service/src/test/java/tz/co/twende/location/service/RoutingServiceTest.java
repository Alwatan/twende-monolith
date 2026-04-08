package tz.co.twende.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tz.co.twende.location.dto.LatLng;
import tz.co.twende.location.dto.Route;
import tz.co.twende.location.provider.ProviderFactory;
import tz.co.twende.location.provider.RoutingProvider;

@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {

    @Mock private ProviderFactory providerFactory;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private RoutingProvider routingProvider;

    @InjectMocks private RoutingService routingService;

    @Test
    void givenCachedRoute_whenGetRoute_thenReturnFromCache() {
        UUID cityId = UUID.randomUUID();
        LatLng origin = new LatLng(new BigDecimal("-6.792"), new BigDecimal("39.208"));
        LatLng dest = new LatLng(new BigDecimal("-6.800"), new BigDecimal("39.250"));
        Route cached = Route.builder().distanceMetres(5000).durationSeconds(600).build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(cached);

        Route result = routingService.getRoute(origin, dest, cityId);

        assertThat(result.getDistanceMetres()).isEqualTo(5000);
    }

    @Test
    void givenNoCachedRoute_whenGetRoute_thenCallProviderAndCache() {
        UUID cityId = UUID.randomUUID();
        LatLng origin = new LatLng(new BigDecimal("-6.792"), new BigDecimal("39.208"));
        LatLng dest = new LatLng(new BigDecimal("-6.800"), new BigDecimal("39.250"));
        Route providerRoute = Route.builder().distanceMetres(5000).durationSeconds(600).build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(providerFactory.routingFor(cityId)).thenReturn(routingProvider);
        when(routingProvider.getRoute(origin, dest)).thenReturn(providerRoute);

        Route result = routingService.getRoute(origin, dest, cityId);

        assertThat(result.getDistanceMetres()).isEqualTo(5000);
        verify(valueOperations).set(anyString(), any(Route.class), anyLong(), any());
    }

    @Test
    void givenRoute_whenGetEtaMinutes_thenCalculateFromDuration() {
        UUID cityId = UUID.randomUUID();
        LatLng origin = new LatLng(new BigDecimal("-6.792"), new BigDecimal("39.208"));
        LatLng dest = new LatLng(new BigDecimal("-6.800"), new BigDecimal("39.250"));
        Route route = Route.builder().distanceMetres(5000).durationSeconds(660).build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(route);

        int eta = routingService.getEtaMinutes(origin, dest, cityId);
        assertThat(eta).isEqualTo(11); // ceil(660/60) = 11
    }
}
