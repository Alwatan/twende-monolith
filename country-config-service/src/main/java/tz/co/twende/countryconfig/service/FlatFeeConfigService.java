package tz.co.twende.countryconfig.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.countryconfig.config.KafkaConfig;
import tz.co.twende.countryconfig.dto.CountryConfigUpdatedEvent;
import tz.co.twende.countryconfig.dto.FlatFeeConfigDto;
import tz.co.twende.countryconfig.dto.UpdateFlatFeeRequest;
import tz.co.twende.countryconfig.entity.FlatFeeConfig;
import tz.co.twende.countryconfig.mapper.CountryConfigMapper;
import tz.co.twende.countryconfig.repository.CountryConfigRepository;
import tz.co.twende.countryconfig.repository.FlatFeeConfigRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlatFeeConfigService {

    private static final String CACHE_KEY_PREFIX = "country:config:";

    private final FlatFeeConfigRepository flatFeeConfigRepository;
    private final CountryConfigRepository countryConfigRepository;
    private final CountryConfigMapper mapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** Get all active flat fee configs for a country. */
    public List<FlatFeeConfigDto> getFlatFeeConfigs(String countryCode) {
        verifyCountryExists(countryCode);
        return mapper.toFlatFeeDtoList(
                flatFeeConfigRepository.findByCountryCodeAndActiveTrue(countryCode));
    }

    /** Get flat fee config for a specific country and service category. */
    public FlatFeeConfigDto getFlatFeeConfig(String countryCode, String serviceCategory) {
        FlatFeeConfig config =
                flatFeeConfigRepository
                        .findByCountryCodeAndServiceCategoryAndActiveTrue(
                                countryCode, serviceCategory.toUpperCase())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Flat fee config not found for country "
                                                        + countryCode
                                                        + " and category "
                                                        + serviceCategory));
        return mapper.toDto(config);
    }

    /** Update flat fee percentage for a country and service category. Admin only. */
    @Transactional
    public FlatFeeConfigDto updateFlatFeeConfig(
            String countryCode, String serviceCategory, UpdateFlatFeeRequest request) {
        FlatFeeConfig config =
                flatFeeConfigRepository
                        .findByCountryCodeAndServiceCategoryAndActiveTrue(
                                countryCode, serviceCategory.toUpperCase())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Flat fee config not found for country "
                                                        + countryCode
                                                        + " and category "
                                                        + serviceCategory));

        config.setPercentage(request.getPercentage());
        flatFeeConfigRepository.save(config);
        evictAndPublish(countryCode);
        log.info(
                "Updated flat fee for country {} category {} to {}%",
                countryCode, serviceCategory, request.getPercentage());

        return mapper.toDto(config);
    }

    private void verifyCountryExists(String countryCode) {
        countryConfigRepository
                .findById(countryCode)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Country not found: " + countryCode));
    }

    private void evictAndPublish(String countryCode) {
        String cacheKey = CACHE_KEY_PREFIX + countryCode;
        redisTemplate.delete(cacheKey);
        log.debug("Evicted cache for key: {}", cacheKey);

        CountryConfigUpdatedEvent event = new CountryConfigUpdatedEvent(countryCode);
        kafkaTemplate.send(KafkaConfig.TOPIC_COUNTRY_UPDATED, countryCode, event);
        log.debug("Published config update event for country: {}", countryCode);
    }
}
