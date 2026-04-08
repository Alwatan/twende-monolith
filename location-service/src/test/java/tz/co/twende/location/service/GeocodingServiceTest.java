package tz.co.twende.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.location.dto.GeocodingResult;
import tz.co.twende.location.entity.GeocodeCache;
import tz.co.twende.location.provider.GeocodingProvider;
import tz.co.twende.location.provider.ProviderFactory;
import tz.co.twende.location.repository.GeocodeCacheRepository;

@ExtendWith(MockitoExtension.class)
class GeocodingServiceTest {

    @Mock private GeocodeCacheRepository geocodeCacheRepository;
    @Mock private ProviderFactory providerFactory;
    @Mock private GeocodingProvider geocodingProvider;

    @InjectMocks private GeocodingService geocodingService;

    @Test
    void givenCachedGeocode_whenSameQueryRequested_thenCacheHitAndHitCountIncremented() {
        UUID cityId = UUID.randomUUID();
        String address = "Dar es Salaam";
        GeocodeCache cached =
                createCache(address, "-6.7924", "39.2083", Instant.now().plus(10, ChronoUnit.DAYS));

        when(geocodeCacheRepository.findByQueryHash(any())).thenReturn(Optional.of(cached));

        GeocodingResult result = geocodingService.geocode(address, null, cityId);

        assertThat(result).isNotNull();
        assertThat(result.getLatitude()).isEqualByComparingTo(new BigDecimal("-6.7924"));
        assertThat(cached.getHitCount()).isEqualTo(2);
        verify(geocodeCacheRepository).save(cached);
        verify(providerFactory, never()).geocodingFor(any());
    }

    @Test
    void givenNoCachedGeocode_whenGeocodeRequested_thenProviderCalledAndResultCached() {
        UUID cityId = UUID.randomUUID();
        String address = "Arusha";
        GeocodingResult providerResult =
                GeocodingResult.builder()
                        .latitude(new BigDecimal("-3.3869"))
                        .longitude(new BigDecimal("36.6830"))
                        .formattedAddress("Arusha, Tanzania")
                        .build();

        when(geocodeCacheRepository.findByQueryHash(any())).thenReturn(Optional.empty());
        when(providerFactory.geocodingFor(cityId)).thenReturn(geocodingProvider);
        when(geocodingProvider.geocode("Arusha", null)).thenReturn(providerResult);
        when(geocodingProvider.getId()).thenReturn("google");

        GeocodingResult result = geocodingService.geocode(address, null, cityId);

        assertThat(result).isNotNull();
        assertThat(result.getLatitude()).isEqualByComparingTo(new BigDecimal("-3.3869"));
        verify(geocodeCacheRepository).save(any(GeocodeCache.class));
    }

    @Test
    void givenExpiredCache_whenGeocodeRequested_thenProviderCalled() {
        UUID cityId = UUID.randomUUID();
        String address = "Mwanza";
        GeocodeCache expired =
                createCache(address, "-2.5167", "32.9000", Instant.now().minus(1, ChronoUnit.DAYS));
        GeocodingResult providerResult =
                GeocodingResult.builder()
                        .latitude(new BigDecimal("-2.5167"))
                        .longitude(new BigDecimal("32.9000"))
                        .formattedAddress("Mwanza, Tanzania")
                        .build();

        when(geocodeCacheRepository.findByQueryHash(any())).thenReturn(Optional.of(expired));
        when(providerFactory.geocodingFor(cityId)).thenReturn(geocodingProvider);
        when(geocodingProvider.geocode("Mwanza", null)).thenReturn(providerResult);
        when(geocodingProvider.getId()).thenReturn("google");

        GeocodingResult result = geocodingService.geocode(address, null, cityId);

        assertThat(result).isNotNull();
        verify(providerFactory).geocodingFor(cityId);
    }

    @Test
    void givenSha256_whenHashing_thenConsistentResult() {
        String hash1 = GeocodingService.sha256("dar es salaam");
        String hash2 = GeocodingService.sha256("dar es salaam");
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }

    @Test
    void givenDifferentInputs_whenSha256_thenDifferentHashes() {
        String hash1 = GeocodingService.sha256("dar es salaam");
        String hash2 = GeocodingService.sha256("arusha");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    private GeocodeCache createCache(String query, String lat, String lng, Instant expiresAt) {
        GeocodeCache cache = new GeocodeCache();
        cache.setQueryHash(GeocodingService.sha256(query.toLowerCase().strip()));
        cache.setQuery(query);
        cache.setLatitude(new BigDecimal(lat));
        cache.setLongitude(new BigDecimal(lng));
        cache.setAddress(query + ", Tanzania");
        cache.setProvider("google");
        cache.setHitCount(1);
        cache.setExpiresAt(expiresAt);
        cache.setCountryCode("TZ");
        return cache;
    }
}
