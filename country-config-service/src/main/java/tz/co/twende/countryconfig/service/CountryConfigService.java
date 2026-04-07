package tz.co.twende.countryconfig.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.countryconfig.config.KafkaConfig;
import tz.co.twende.countryconfig.dto.*;
import tz.co.twende.countryconfig.entity.CountryConfig;
import tz.co.twende.countryconfig.entity.CountryConfig.CountryStatus;
import tz.co.twende.countryconfig.entity.OperatingCity;
import tz.co.twende.countryconfig.entity.VehicleTypeConfig;
import tz.co.twende.countryconfig.mapper.CountryConfigMapper;
import tz.co.twende.countryconfig.repository.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CountryConfigService {

    private static final String CACHE_KEY_PREFIX = "country:config:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final CountryConfigRepository countryConfigRepository;
    private final VehicleTypeConfigRepository vehicleTypeConfigRepository;
    private final OperatingCityRepository operatingCityRepository;
    private final PaymentMethodConfigRepository paymentMethodConfigRepository;
    private final RequiredDriverDocumentRepository requiredDriverDocumentRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CountryConfigMapper mapper;
    private final ObjectMapper objectMapper;

    /**
     * Get full country configuration with nested vehicle types, cities, payment methods, and
     * required documents. Uses Redis cache-through pattern with 5-minute TTL.
     */
    public CountryConfigDto getConfig(String countryCode) {
        String cacheKey = CACHE_KEY_PREFIX + countryCode;

        // 1. Try Redis cache
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof CountryConfigDto dto) {
            log.debug("Cache hit for country config: {}", countryCode);
            return dto;
        }

        // 2. Load from DB
        log.debug("Cache miss for country config: {}, loading from DB", countryCode);
        CountryConfig config =
                countryConfigRepository
                        .findById(countryCode)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Country not found: " + countryCode));

        // 3. Assemble DTO with nested lists
        CountryConfigDto dto = mapper.toDto(config);
        dto.setVehicleTypes(
                mapper.toVehicleTypeDtoList(
                        vehicleTypeConfigRepository.findByCountryCode(countryCode)));
        dto.setCities(mapper.toCityDtoList(operatingCityRepository.findByCountryCode(countryCode)));
        dto.setPaymentMethods(
                mapper.toPaymentMethodDtoList(
                        paymentMethodConfigRepository.findByCountryCode(countryCode)));
        dto.setRequiredDocuments(
                mapper.toDocumentDtoList(
                        requiredDriverDocumentRepository.findByCountryCode(countryCode)));

        // 4. Cache with TTL
        redisTemplate.opsForValue().set(cacheKey, dto, CACHE_TTL);
        return dto;
    }

    /** Get all active country codes. */
    public List<String> getActiveCountries() {
        return countryConfigRepository.findByStatus(CountryStatus.ACTIVE).stream()
                .map(CountryConfig::getCode)
                .toList();
    }

    /** Get all country configs (including inactive) for admin view. */
    public List<CountryConfigDto> getAllConfigs() {
        return countryConfigRepository.findAll().stream()
                .map(
                        config -> {
                            CountryConfigDto dto = mapper.toDto(config);
                            dto.setVehicleTypes(
                                    mapper.toVehicleTypeDtoList(
                                            vehicleTypeConfigRepository.findByCountryCode(
                                                    config.getCode())));
                            dto.setCities(
                                    mapper.toCityDtoList(
                                            operatingCityRepository.findByCountryCode(
                                                    config.getCode())));
                            dto.setPaymentMethods(
                                    mapper.toPaymentMethodDtoList(
                                            paymentMethodConfigRepository.findByCountryCode(
                                                    config.getCode())));
                            dto.setRequiredDocuments(
                                    mapper.toDocumentDtoList(
                                            requiredDriverDocumentRepository.findByCountryCode(
                                                    config.getCode())));
                            return dto;
                        })
                .toList();
    }

    /** Get vehicle types for a country. */
    public List<VehicleTypeConfigDto> getVehicleTypes(String countryCode) {
        return mapper.toVehicleTypeDtoList(
                vehicleTypeConfigRepository.findByCountryCode(countryCode));
    }

    /** Get operating cities for a country. */
    public List<OperatingCityDto> getCities(String countryCode) {
        return mapper.toCityDtoList(operatingCityRepository.findByCountryCode(countryCode));
    }

    /** Update country config fields. Evicts cache and publishes Kafka event. */
    @Transactional
    public void updateConfig(String countryCode, UpdateCountryConfigRequest request) {
        CountryConfig config =
                countryConfigRepository
                        .findById(countryCode)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Country not found: " + countryCode));

        if (request.getName() != null) {
            config.setName(request.getName());
        }
        if (request.getStatus() != null) {
            config.setStatus(CountryStatus.valueOf(request.getStatus()));
        }
        if (request.getDefaultLocale() != null) {
            config.setDefaultLocale(request.getDefaultLocale());
        }
        if (request.getSupportedLocales() != null) {
            config.setSupportedLocales(request.getSupportedLocales());
        }
        if (request.getDateFormat() != null) {
            config.setDateFormat(request.getDateFormat());
        }
        if (request.getDistanceUnit() != null) {
            config.setDistanceUnit(request.getDistanceUnit());
        }
        if (request.getTimeFormat() != null) {
            config.setTimeFormat(request.getTimeFormat());
        }
        if (request.getCurrencyCode() != null) {
            config.setCurrencyCode(request.getCurrencyCode());
        }
        if (request.getCurrencySymbol() != null) {
            config.setCurrencySymbol(request.getCurrencySymbol());
        }
        if (request.getMinorUnits() != null) {
            config.setMinorUnits(request.getMinorUnits());
        }
        if (request.getDisplayFormat() != null) {
            config.setDisplayFormat(request.getDisplayFormat());
        }
        if (request.getPhonePrefix() != null) {
            config.setPhonePrefix(request.getPhonePrefix());
        }
        if (request.getCashEnabled() != null) {
            config.setCashEnabled(request.getCashEnabled());
        }
        if (request.getSubscriptionAggregator() != null) {
            config.setSubscriptionAggregator(request.getSubscriptionAggregator());
        }
        if (request.getSmsProvider() != null) {
            config.setSmsProvider(request.getSmsProvider());
        }
        if (request.getPushProvider() != null) {
            config.setPushProvider(request.getPushProvider());
        }
        if (request.getRegulatoryAuthority() != null) {
            config.setRegulatoryAuthority(request.getRegulatoryAuthority());
        }
        if (request.getTripReportingRequired() != null) {
            config.setTripReportingRequired(request.getTripReportingRequired());
        }
        if (request.getDataRetentionDays() != null) {
            config.setDataRetentionDays(request.getDataRetentionDays());
        }
        if (request.getFeatures() != null) {
            config.setFeatures(request.getFeatures());
        }

        countryConfigRepository.save(config);
        evictAndPublish(countryCode);
        log.info("Updated country config for: {}", countryCode);
    }

    /** Update feature flags JSONB. Evicts cache and publishes Kafka event. */
    @Transactional
    public void updateFeatures(String countryCode, Map<String, Object> features) {
        CountryConfig config =
                countryConfigRepository
                        .findById(countryCode)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Country not found: " + countryCode));

        try {
            String featuresJson = objectMapper.writeValueAsString(features);
            config.setFeatures(featuresJson);
        } catch (JacksonException e) {
            throw new BadRequestException("Invalid features format: " + e.getMessage());
        }

        countryConfigRepository.save(config);
        evictAndPublish(countryCode);
        log.info("Updated features for country: {}", countryCode);
    }

    /**
     * Update country status (ACTIVE, COMING_SOON, INACTIVE). Evicts cache and publishes Kafka
     * event.
     */
    @Transactional
    public void updateStatus(String countryCode, String status) {
        CountryConfig config =
                countryConfigRepository
                        .findById(countryCode)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Country not found: " + countryCode));

        config.setStatus(CountryStatus.valueOf(status));
        countryConfigRepository.save(config);
        evictAndPublish(countryCode);
        log.info("Updated status for country {} to: {}", countryCode, status);
    }

    /**
     * Update a vehicle type config. Validates country code match. Evicts cache and publishes Kafka
     * event.
     */
    @Transactional
    public void updateVehicleType(String countryCode, UUID id, VehicleTypeConfigDto dto) {
        VehicleTypeConfig vehicleType =
                vehicleTypeConfigRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Vehicle type config not found: " + id));

        if (!vehicleType.getCountryCode().equals(countryCode)) {
            throw new ResourceNotFoundException(
                    "Vehicle type " + id + " does not belong to country " + countryCode);
        }

        if (dto.getDisplayName() != null) {
            vehicleType.setDisplayName(dto.getDisplayName());
        }
        if (dto.getMaxPassengers() != null) {
            vehicleType.setMaxPassengers(dto.getMaxPassengers());
        }
        if (dto.getBaseFare() != null) {
            vehicleType.setBaseFare(dto.getBaseFare());
        }
        if (dto.getPerKm() != null) {
            vehicleType.setPerKm(dto.getPerKm());
        }
        if (dto.getPerMinute() != null) {
            vehicleType.setPerMinute(dto.getPerMinute());
        }
        if (dto.getMinimumFare() != null) {
            vehicleType.setMinimumFare(dto.getMinimumFare());
        }
        if (dto.getCancellationFee() != null) {
            vehicleType.setCancellationFee(dto.getCancellationFee());
        }
        if (dto.getSurgeMultiplierCap() != null) {
            vehicleType.setSurgeMultiplierCap(dto.getSurgeMultiplierCap());
        }
        if (dto.getIsActive() != null) {
            vehicleType.setIsActive(dto.getIsActive());
        }

        vehicleTypeConfigRepository.save(vehicleType);
        evictAndPublish(countryCode);
        log.info("Updated vehicle type {} for country: {}", id, countryCode);
    }

    /** Create a new operating city. Evicts cache and publishes Kafka event. */
    @Transactional
    public OperatingCityDto createCity(String countryCode, CreateCityRequest request) {
        // Verify country exists
        countryConfigRepository
                .findById(countryCode)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Country not found: " + countryCode));

        OperatingCity city = new OperatingCity();
        city.setCountryCode(countryCode);
        city.setCityId(request.getCityId());
        city.setName(request.getName());
        city.setTimezone(request.getTimezone());
        city.setStatus("COMING_SOON");
        city.setCenterLat(request.getCenterLat());
        city.setCenterLng(request.getCenterLng());
        city.setRadiusKm(request.getRadiusKm());
        city.setGeocodingProvider("GOOGLE");
        city.setRoutingProvider("GOOGLE");
        city.setAutocompleteProvider("GOOGLE");

        OperatingCity saved = operatingCityRepository.save(city);
        evictAndPublish(countryCode);
        log.info("Created operating city {} for country: {}", saved.getCityId(), countryCode);

        return mapper.toDto(saved);
    }

    /**
     * Evict Redis cache for the given country and publish a Kafka event so all consuming services
     * invalidate their local caches.
     */
    private void evictAndPublish(String countryCode) {
        String cacheKey = CACHE_KEY_PREFIX + countryCode;
        redisTemplate.delete(cacheKey);
        log.debug("Evicted cache for key: {}", cacheKey);

        CountryConfigUpdatedEvent event = new CountryConfigUpdatedEvent(countryCode);
        kafkaTemplate.send(KafkaConfig.TOPIC_COUNTRY_UPDATED, countryCode, event);
        log.debug("Published config update event for country: {}", countryCode);
    }
}
