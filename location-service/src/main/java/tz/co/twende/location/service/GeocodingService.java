package tz.co.twende.location.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.location.dto.GeocodingResult;
import tz.co.twende.location.dto.LatLng;
import tz.co.twende.location.entity.GeocodeCache;
import tz.co.twende.location.provider.ProviderFactory;
import tz.co.twende.location.repository.GeocodeCacheRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeocodingService {

    private final GeocodeCacheRepository geocodeCacheRepository;
    private final ProviderFactory providerFactory;

    @Transactional
    public GeocodingResult geocode(String address, LatLng bias, UUID cityId) {
        String hash = sha256(address.toLowerCase().strip());
        Optional<GeocodeCache> cached = geocodeCacheRepository.findByQueryHash(hash);
        if (cached.isPresent()
                && cached.get().getExpiresAt() != null
                && cached.get().getExpiresAt().isAfter(Instant.now())) {
            GeocodeCache entry = cached.get();
            entry.setHitCount(entry.getHitCount() + 1);
            geocodeCacheRepository.save(entry);
            return toResult(entry);
        }

        var provider = providerFactory.geocodingFor(cityId);
        GeocodingResult result = provider.geocode(address, bias);
        if (result != null) {
            saveToCache(hash, address, result, provider.getId());
        }
        return result;
    }

    public GeocodingResult reverseGeocode(LatLng point, UUID cityId) {
        var provider = providerFactory.geocodingFor(cityId);
        return provider.reverseGeocode(point);
    }

    @Scheduled(cron = "0 0 3 * * SUN")
    @Transactional
    public void cleanupExpiredCache() {
        int deleted = geocodeCacheRepository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired geocode cache entries", deleted);
        }
    }

    private void saveToCache(String hash, String query, GeocodingResult result, String providerId) {
        GeocodeCache entry = new GeocodeCache();
        entry.setQueryHash(hash);
        entry.setQuery(query);
        entry.setLatitude(result.getLatitude());
        entry.setLongitude(result.getLongitude());
        entry.setAddress(result.getFormattedAddress());
        entry.setProvider(providerId);
        entry.setHitCount(1);
        entry.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        entry.setCountryCode("TZ");
        geocodeCacheRepository.save(entry);
    }

    private GeocodingResult toResult(GeocodeCache cache) {
        return GeocodingResult.builder()
                .latitude(cache.getLatitude())
                .longitude(cache.getLongitude())
                .formattedAddress(cache.getAddress())
                .build();
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
