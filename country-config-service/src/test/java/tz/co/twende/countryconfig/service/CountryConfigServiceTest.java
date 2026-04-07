package tz.co.twende.countryconfig.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.countryconfig.dto.*;
import tz.co.twende.countryconfig.entity.CountryConfig;
import tz.co.twende.countryconfig.entity.CountryConfig.CountryStatus;
import tz.co.twende.countryconfig.entity.OperatingCity;
import tz.co.twende.countryconfig.entity.VehicleTypeConfig;
import tz.co.twende.countryconfig.mapper.CountryConfigMapper;
import tz.co.twende.countryconfig.repository.*;

@ExtendWith(MockitoExtension.class)
class CountryConfigServiceTest {

    @Mock private CountryConfigRepository countryConfigRepository;
    @Mock private VehicleTypeConfigRepository vehicleTypeConfigRepository;
    @Mock private OperatingCityRepository operatingCityRepository;
    @Mock private PaymentMethodConfigRepository paymentMethodConfigRepository;
    @Mock private RequiredDriverDocumentRepository requiredDriverDocumentRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private CountryConfigMapper mapper;
    @Mock private ObjectMapper objectMapper;
    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks private CountryConfigService countryConfigService;

    private CountryConfig tanzaniaConfig;
    private CountryConfigDto tanzaniaDto;

    @BeforeEach
    void setUp() {
        tanzaniaConfig = new CountryConfig();
        tanzaniaConfig.setCode("TZ");
        tanzaniaConfig.setName("Tanzania");
        tanzaniaConfig.setStatus(CountryStatus.ACTIVE);
        tanzaniaConfig.setDefaultLocale("sw-TZ");
        tanzaniaConfig.setCurrencyCode("TZS");
        tanzaniaConfig.setCurrencySymbol("TSh");
        tanzaniaConfig.setMinorUnits(0);
        tanzaniaConfig.setDisplayFormat("TSh {amount}");
        tanzaniaConfig.setPhonePrefix("+255");
        tanzaniaConfig.setCashEnabled(true);
        tanzaniaConfig.setSmsProvider("AFRICASTALKING");
        tanzaniaConfig.setPushProvider("FCM");
        tanzaniaConfig.setTripReportingRequired(true);
        tanzaniaConfig.setDataRetentionDays(365);
        tanzaniaConfig.setFeatures("{}");
        tanzaniaConfig.setSupportedLocales(new String[] {"sw-TZ", "en-TZ"});
        tanzaniaConfig.setDateFormat("DD/MM/YYYY");
        tanzaniaConfig.setDistanceUnit("km");
        tanzaniaConfig.setTimeFormat("12h");

        tanzaniaDto =
                CountryConfigDto.builder()
                        .code("TZ")
                        .name("Tanzania")
                        .status("ACTIVE")
                        .defaultLocale("sw-TZ")
                        .currencyCode("TZS")
                        .currencySymbol("TSh")
                        .minorUnits(0)
                        .displayFormat("TSh {amount}")
                        .phonePrefix("+255")
                        .cashEnabled(true)
                        .smsProvider("AFRICASTALKING")
                        .pushProvider("FCM")
                        .tripReportingRequired(true)
                        .dataRetentionDays(365)
                        .features("{}")
                        .build();
    }

    @Test
    void givenCachedConfig_whenGetConfig_thenReturnsCachedDto() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("country:config:TZ")).thenReturn(tanzaniaDto);

        CountryConfigDto result = countryConfigService.getConfig("TZ");

        assertThat(result).isEqualTo(tanzaniaDto);
        verify(countryConfigRepository, never()).findById(anyString());
    }

    @Test
    void givenNoCachedConfig_whenGetConfig_thenLoadsFromDbAndCaches() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("country:config:TZ")).thenReturn(null);
        when(countryConfigRepository.findById("TZ")).thenReturn(Optional.of(tanzaniaConfig));
        when(mapper.toDto(tanzaniaConfig)).thenReturn(tanzaniaDto);
        when(vehicleTypeConfigRepository.findByCountryCode("TZ")).thenReturn(List.of());
        when(operatingCityRepository.findByCountryCode("TZ")).thenReturn(List.of());
        when(paymentMethodConfigRepository.findByCountryCode("TZ")).thenReturn(List.of());
        when(requiredDriverDocumentRepository.findByCountryCode("TZ")).thenReturn(List.of());
        when(mapper.toVehicleTypeDtoList(anyList())).thenReturn(List.of());
        when(mapper.toCityDtoList(anyList())).thenReturn(List.of());
        when(mapper.toPaymentMethodDtoList(anyList())).thenReturn(List.of());
        when(mapper.toDocumentDtoList(anyList())).thenReturn(List.of());

        CountryConfigDto result = countryConfigService.getConfig("TZ");

        assertThat(result).isEqualTo(tanzaniaDto);
        verify(valueOperations).set(eq("country:config:TZ"), eq(tanzaniaDto), any(Duration.class));
    }

    @Test
    void givenNonexistentCountry_whenGetConfig_thenThrowsResourceNotFound() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("country:config:XX")).thenReturn(null);
        when(countryConfigRepository.findById("XX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> countryConfigService.getConfig("XX"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Country not found: XX");
    }

    @Test
    void givenActiveCountries_whenGetActiveCountries_thenReturnsOnlyActiveCodes() {
        CountryConfig activeConfig = new CountryConfig();
        activeConfig.setCode("TZ");
        activeConfig.setStatus(CountryStatus.ACTIVE);

        CountryConfig inactiveConfig = new CountryConfig();
        inactiveConfig.setCode("KE");
        inactiveConfig.setStatus(CountryStatus.COMING_SOON);

        when(countryConfigRepository.findByStatus(CountryStatus.ACTIVE))
                .thenReturn(List.of(activeConfig));

        List<String> result = countryConfigService.getActiveCountries();

        assertThat(result).containsExactly("TZ");
        assertThat(result).doesNotContain("KE");
    }

    @Test
    void givenVehicleTypes_whenGetVehicleTypes_thenReturnsDtos() {
        VehicleTypeConfig bajaj = new VehicleTypeConfig();
        bajaj.setCountryCode("TZ");
        bajaj.setVehicleType("BAJAJ");
        bajaj.setDisplayName("Bajaj");
        bajaj.setMaxPassengers(2);
        bajaj.setBaseFare(new BigDecimal("500"));
        bajaj.setPerKm(new BigDecimal("200"));
        bajaj.setPerMinute(new BigDecimal("20"));
        bajaj.setMinimumFare(new BigDecimal("1000"));
        bajaj.setCancellationFee(new BigDecimal("200"));
        bajaj.setSurgeMultiplierCap(new BigDecimal("2.50"));
        bajaj.setIsActive(true);

        VehicleTypeConfigDto bajajDto =
                VehicleTypeConfigDto.builder()
                        .countryCode("TZ")
                        .vehicleType("BAJAJ")
                        .displayName("Bajaj")
                        .maxPassengers(2)
                        .baseFare(new BigDecimal("500"))
                        .build();

        when(vehicleTypeConfigRepository.findByCountryCode("TZ")).thenReturn(List.of(bajaj));
        when(mapper.toVehicleTypeDtoList(List.of(bajaj))).thenReturn(List.of(bajajDto));

        List<VehicleTypeConfigDto> result = countryConfigService.getVehicleTypes("TZ");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVehicleType()).isEqualTo("BAJAJ");
    }

    @Test
    void givenUpdateConfig_whenUpdate_thenSavesAndEvictsAndPublishes() {
        when(countryConfigRepository.findById("TZ")).thenReturn(Optional.of(tanzaniaConfig));
        when(redisTemplate.delete("country:config:TZ")).thenReturn(true);

        UpdateCountryConfigRequest request =
                UpdateCountryConfigRequest.builder().name("United Republic of Tanzania").build();

        countryConfigService.updateConfig("TZ", request);

        assertThat(tanzaniaConfig.getName()).isEqualTo("United Republic of Tanzania");
        verify(countryConfigRepository).save(tanzaniaConfig);
        verify(redisTemplate).delete("country:config:TZ");
        verify(kafkaTemplate)
                .send(
                        eq("twende.config.country-updated"),
                        eq("TZ"),
                        any(CountryConfigUpdatedEvent.class));
    }

    @Test
    void givenUpdateFeatures_whenUpdate_thenUpdatesJsonbAndEvicts() throws JacksonException {
        when(countryConfigRepository.findById("TZ")).thenReturn(Optional.of(tanzaniaConfig));
        when(redisTemplate.delete("country:config:TZ")).thenReturn(true);

        Map<String, Object> features = Map.of("surgeEnabled", true, "loyaltyEnabled", false);
        String featuresJson = "{\"surgeEnabled\":true,\"loyaltyEnabled\":false}";
        when(objectMapper.writeValueAsString(features)).thenReturn(featuresJson);

        countryConfigService.updateFeatures("TZ", features);

        assertThat(tanzaniaConfig.getFeatures()).isEqualTo(featuresJson);
        verify(countryConfigRepository).save(tanzaniaConfig);
        verify(redisTemplate).delete("country:config:TZ");
        verify(kafkaTemplate)
                .send(
                        eq("twende.config.country-updated"),
                        eq("TZ"),
                        any(CountryConfigUpdatedEvent.class));
    }

    @Test
    void givenInvalidFeaturesJson_whenUpdateFeatures_thenThrowsBadRequest()
            throws JacksonException {
        when(countryConfigRepository.findById("TZ")).thenReturn(Optional.of(tanzaniaConfig));

        Map<String, Object> features = Map.of("bad", "data");
        when(objectMapper.writeValueAsString(features)).thenThrow(new JacksonException("fail") {});

        assertThatThrownBy(() -> countryConfigService.updateFeatures("TZ", features))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid features format");
    }

    @Test
    void givenNonexistentCountry_whenUpdateConfig_thenThrowsResourceNotFound() {
        when(countryConfigRepository.findById("XX")).thenReturn(Optional.empty());

        UpdateCountryConfigRequest request =
                UpdateCountryConfigRequest.builder().name("Unknown").build();

        assertThatThrownBy(() -> countryConfigService.updateConfig("XX", request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Country not found: XX");
    }

    @Test
    void givenValidStatus_whenUpdateStatus_thenSavesAndEvicts() {
        when(countryConfigRepository.findById("TZ")).thenReturn(Optional.of(tanzaniaConfig));
        when(redisTemplate.delete("country:config:TZ")).thenReturn(true);

        countryConfigService.updateStatus("TZ", "INACTIVE");

        assertThat(tanzaniaConfig.getStatus()).isEqualTo(CountryStatus.INACTIVE);
        verify(countryConfigRepository).save(tanzaniaConfig);
        verify(redisTemplate).delete("country:config:TZ");
    }

    @Test
    void givenVehicleTypeBelongsToDifferentCountry_whenUpdateVehicleType_thenThrowsNotFound() {
        UUID vtId = UUID.randomUUID();
        VehicleTypeConfig vtConfig = new VehicleTypeConfig();
        vtConfig.setCountryCode("KE");

        when(vehicleTypeConfigRepository.findById(vtId)).thenReturn(Optional.of(vtConfig));

        VehicleTypeConfigDto dto = VehicleTypeConfigDto.builder().displayName("Updated").build();

        assertThatThrownBy(() -> countryConfigService.updateVehicleType("TZ", vtId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("does not belong to country TZ");
    }

    @Test
    void givenValidVehicleType_whenUpdateVehicleType_thenSavesAndEvicts() {
        UUID vtId = UUID.randomUUID();
        VehicleTypeConfig vtConfig = new VehicleTypeConfig();
        vtConfig.setCountryCode("TZ");
        vtConfig.setDisplayName("Bajaj");
        vtConfig.setBaseFare(new BigDecimal("500"));

        when(vehicleTypeConfigRepository.findById(vtId)).thenReturn(Optional.of(vtConfig));
        when(redisTemplate.delete("country:config:TZ")).thenReturn(true);

        VehicleTypeConfigDto dto =
                VehicleTypeConfigDto.builder()
                        .displayName("Bajaji")
                        .baseFare(new BigDecimal("600"))
                        .build();

        countryConfigService.updateVehicleType("TZ", vtId, dto);

        assertThat(vtConfig.getDisplayName()).isEqualTo("Bajaji");
        assertThat(vtConfig.getBaseFare()).isEqualByComparingTo(new BigDecimal("600"));
        verify(vehicleTypeConfigRepository).save(vtConfig);
        verify(redisTemplate).delete("country:config:TZ");
    }

    @Test
    void givenValidRequest_whenCreateCity_thenSavesAndReturnsDto() {
        when(countryConfigRepository.findById("TZ")).thenReturn(Optional.of(tanzaniaConfig));
        when(redisTemplate.delete("country:config:TZ")).thenReturn(true);

        CreateCityRequest request =
                CreateCityRequest.builder()
                        .cityId("mwanza")
                        .name("Mwanza")
                        .timezone("Africa/Dar_es_Salaam")
                        .centerLat(-2.5164)
                        .centerLng(32.9175)
                        .radiusKm(15)
                        .build();

        OperatingCity savedCity = new OperatingCity();
        savedCity.setCountryCode("TZ");
        savedCity.setCityId("mwanza");
        savedCity.setName("Mwanza");

        OperatingCityDto cityDto =
                OperatingCityDto.builder()
                        .countryCode("TZ")
                        .cityId("mwanza")
                        .name("Mwanza")
                        .build();

        when(operatingCityRepository.save(any(OperatingCity.class))).thenReturn(savedCity);
        when(mapper.toDto(savedCity)).thenReturn(cityDto);

        OperatingCityDto result = countryConfigService.createCity("TZ", request);

        assertThat(result.getCityId()).isEqualTo("mwanza");
        assertThat(result.getName()).isEqualTo("Mwanza");
        verify(operatingCityRepository).save(any(OperatingCity.class));
        verify(redisTemplate).delete("country:config:TZ");
    }

    @Test
    void givenNonexistentCountry_whenCreateCity_thenThrowsResourceNotFound() {
        when(countryConfigRepository.findById("XX")).thenReturn(Optional.empty());

        CreateCityRequest request =
                CreateCityRequest.builder()
                        .cityId("city")
                        .name("City")
                        .timezone("UTC")
                        .centerLat(0.0)
                        .centerLng(0.0)
                        .radiusKm(10)
                        .build();

        assertThatThrownBy(() -> countryConfigService.createCity("XX", request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Country not found: XX");
    }

    @Test
    void givenMultipleCountries_whenGetAllConfigs_thenReturnsAll() {
        CountryConfig keConfig = new CountryConfig();
        keConfig.setCode("KE");
        keConfig.setName("Kenya");
        keConfig.setStatus(CountryStatus.COMING_SOON);

        CountryConfigDto keDto =
                CountryConfigDto.builder().code("KE").name("Kenya").status("COMING_SOON").build();

        when(countryConfigRepository.findAll()).thenReturn(List.of(tanzaniaConfig, keConfig));
        when(mapper.toDto(tanzaniaConfig)).thenReturn(tanzaniaDto);
        when(mapper.toDto(keConfig)).thenReturn(keDto);
        when(vehicleTypeConfigRepository.findByCountryCode(anyString())).thenReturn(List.of());
        when(operatingCityRepository.findByCountryCode(anyString())).thenReturn(List.of());
        when(paymentMethodConfigRepository.findByCountryCode(anyString())).thenReturn(List.of());
        when(requiredDriverDocumentRepository.findByCountryCode(anyString())).thenReturn(List.of());
        when(mapper.toVehicleTypeDtoList(anyList())).thenReturn(List.of());
        when(mapper.toCityDtoList(anyList())).thenReturn(List.of());
        when(mapper.toPaymentMethodDtoList(anyList())).thenReturn(List.of());
        when(mapper.toDocumentDtoList(anyList())).thenReturn(List.of());

        List<CountryConfigDto> result = countryConfigService.getAllConfigs();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CountryConfigDto::getCode).containsExactly("TZ", "KE");
    }

    @Test
    void givenCountryCode_whenGetCities_thenReturnsDtos() {
        OperatingCity city = new OperatingCity();
        city.setCountryCode("TZ");
        city.setCityId("dar-es-salaam");
        city.setName("Dar es Salaam");

        OperatingCityDto cityDto =
                OperatingCityDto.builder()
                        .countryCode("TZ")
                        .cityId("dar-es-salaam")
                        .name("Dar es Salaam")
                        .build();

        when(operatingCityRepository.findByCountryCode("TZ")).thenReturn(List.of(city));
        when(mapper.toCityDtoList(List.of(city))).thenReturn(List.of(cityDto));

        List<OperatingCityDto> result = countryConfigService.getCities("TZ");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCityId()).isEqualTo("dar-es-salaam");
    }
}
