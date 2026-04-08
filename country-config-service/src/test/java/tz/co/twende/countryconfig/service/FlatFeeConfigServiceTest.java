package tz.co.twende.countryconfig.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.countryconfig.dto.CountryConfigUpdatedEvent;
import tz.co.twende.countryconfig.dto.FlatFeeConfigDto;
import tz.co.twende.countryconfig.dto.UpdateFlatFeeRequest;
import tz.co.twende.countryconfig.entity.CountryConfig;
import tz.co.twende.countryconfig.entity.FlatFeeConfig;
import tz.co.twende.countryconfig.mapper.CountryConfigMapper;
import tz.co.twende.countryconfig.repository.CountryConfigRepository;
import tz.co.twende.countryconfig.repository.FlatFeeConfigRepository;

@ExtendWith(MockitoExtension.class)
class FlatFeeConfigServiceTest {

    @Mock private FlatFeeConfigRepository flatFeeConfigRepository;
    @Mock private CountryConfigRepository countryConfigRepository;
    @Mock private CountryConfigMapper mapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private FlatFeeConfigService flatFeeConfigService;

    private CountryConfig tanzaniaConfig;
    private FlatFeeConfig rideFeeConfig;
    private FlatFeeConfigDto rideFeeDto;

    @BeforeEach
    void setUp() {
        tanzaniaConfig = new CountryConfig();
        tanzaniaConfig.setCode("TZ");
        tanzaniaConfig.setName("Tanzania");

        rideFeeConfig = new FlatFeeConfig();
        rideFeeConfig.setId(UUID.randomUUID());
        rideFeeConfig.setCountryCode("TZ");
        rideFeeConfig.setServiceCategory("RIDE");
        rideFeeConfig.setPercentage(new BigDecimal("15.00"));
        rideFeeConfig.setActive(true);

        rideFeeDto =
                FlatFeeConfigDto.builder()
                        .id(rideFeeConfig.getId())
                        .countryCode("TZ")
                        .serviceCategory("RIDE")
                        .percentage(new BigDecimal("15.00"))
                        .active(true)
                        .build();
    }

    @Test
    void givenActiveConfigs_whenGetFlatFeeConfigs_thenReturnsAllActive() {
        FlatFeeConfig charterFeeConfig = new FlatFeeConfig();
        charterFeeConfig.setId(UUID.randomUUID());
        charterFeeConfig.setCountryCode("TZ");
        charterFeeConfig.setServiceCategory("CHARTER");
        charterFeeConfig.setPercentage(new BigDecimal("12.00"));
        charterFeeConfig.setActive(true);

        FlatFeeConfigDto charterFeeDto =
                FlatFeeConfigDto.builder()
                        .id(charterFeeConfig.getId())
                        .countryCode("TZ")
                        .serviceCategory("CHARTER")
                        .percentage(new BigDecimal("12.00"))
                        .active(true)
                        .build();

        when(countryConfigRepository.findById("TZ")).thenReturn(Optional.of(tanzaniaConfig));
        when(flatFeeConfigRepository.findByCountryCodeAndActiveTrue("TZ"))
                .thenReturn(List.of(rideFeeConfig, charterFeeConfig));
        when(mapper.toFlatFeeDtoList(List.of(rideFeeConfig, charterFeeConfig)))
                .thenReturn(List.of(rideFeeDto, charterFeeDto));

        List<FlatFeeConfigDto> result = flatFeeConfigService.getFlatFeeConfigs("TZ");

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(FlatFeeConfigDto::getServiceCategory)
                .containsExactly("RIDE", "CHARTER");
    }

    @Test
    void givenNonexistentCountry_whenGetFlatFeeConfigs_thenThrowsNotFound() {
        when(countryConfigRepository.findById("XX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> flatFeeConfigService.getFlatFeeConfigs("XX"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Country not found: XX");
    }

    @Test
    void givenExistingConfig_whenGetFlatFeeConfig_thenReturnsDto() {
        when(flatFeeConfigRepository.findByCountryCodeAndServiceCategoryAndActiveTrue("TZ", "RIDE"))
                .thenReturn(Optional.of(rideFeeConfig));
        when(mapper.toDto(rideFeeConfig)).thenReturn(rideFeeDto);

        FlatFeeConfigDto result = flatFeeConfigService.getFlatFeeConfig("TZ", "RIDE");

        assertThat(result.getServiceCategory()).isEqualTo("RIDE");
        assertThat(result.getPercentage()).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    void givenMissingConfig_whenGetFlatFeeConfig_thenThrowsNotFound() {
        when(flatFeeConfigRepository.findByCountryCodeAndServiceCategoryAndActiveTrue(
                        "TZ", "CARGO"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> flatFeeConfigService.getFlatFeeConfig("TZ", "CARGO"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Flat fee config not found");
    }

    @Test
    void givenValidRequest_whenUpdateFlatFeeConfig_thenSavesEvictsAndPublishes() {
        when(flatFeeConfigRepository.findByCountryCodeAndServiceCategoryAndActiveTrue("TZ", "RIDE"))
                .thenReturn(Optional.of(rideFeeConfig));
        when(redisTemplate.delete("country:config:TZ")).thenReturn(true);

        FlatFeeConfigDto updatedDto =
                FlatFeeConfigDto.builder()
                        .id(rideFeeConfig.getId())
                        .countryCode("TZ")
                        .serviceCategory("RIDE")
                        .percentage(new BigDecimal("18.00"))
                        .active(true)
                        .build();
        when(mapper.toDto(rideFeeConfig)).thenReturn(updatedDto);

        UpdateFlatFeeRequest request =
                UpdateFlatFeeRequest.builder().percentage(new BigDecimal("18.00")).build();

        FlatFeeConfigDto result = flatFeeConfigService.updateFlatFeeConfig("TZ", "RIDE", request);

        assertThat(rideFeeConfig.getPercentage()).isEqualByComparingTo(new BigDecimal("18.00"));
        verify(flatFeeConfigRepository).save(rideFeeConfig);
        verify(redisTemplate).delete("country:config:TZ");
        verify(kafkaTemplate)
                .send(
                        eq("twende.config.country-updated"),
                        eq("TZ"),
                        any(CountryConfigUpdatedEvent.class));
        assertThat(result.getPercentage()).isEqualByComparingTo(new BigDecimal("18.00"));
    }

    @Test
    void givenMissingConfig_whenUpdateFlatFeeConfig_thenThrowsNotFound() {
        when(flatFeeConfigRepository.findByCountryCodeAndServiceCategoryAndActiveTrue(
                        "TZ", "CHARTER"))
                .thenReturn(Optional.empty());

        UpdateFlatFeeRequest request =
                UpdateFlatFeeRequest.builder().percentage(new BigDecimal("20.00")).build();

        assertThatThrownBy(() -> flatFeeConfigService.updateFlatFeeConfig("TZ", "CHARTER", request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Flat fee config not found");
    }

    @Test
    void givenCaseInsensitiveCategory_whenGetFlatFeeConfig_thenNormalizesToUpperCase() {
        when(flatFeeConfigRepository.findByCountryCodeAndServiceCategoryAndActiveTrue("TZ", "RIDE"))
                .thenReturn(Optional.of(rideFeeConfig));
        when(mapper.toDto(rideFeeConfig)).thenReturn(rideFeeDto);

        FlatFeeConfigDto result = flatFeeConfigService.getFlatFeeConfig("TZ", "ride");

        assertThat(result.getServiceCategory()).isEqualTo("RIDE");
        verify(flatFeeConfigRepository)
                .findByCountryCodeAndServiceCategoryAndActiveTrue("TZ", "RIDE");
    }
}
