package tz.co.twende.countryconfig.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tz.co.twende.countryconfig.entity.CountryConfig;
import tz.co.twende.countryconfig.entity.CountryConfig.CountryStatus;
import tz.co.twende.countryconfig.entity.OperatingCity;
import tz.co.twende.countryconfig.entity.PaymentMethodConfig;
import tz.co.twende.countryconfig.entity.RequiredDriverDocument;
import tz.co.twende.countryconfig.entity.VehicleTypeConfig;
import tz.co.twende.countryconfig.repository.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class CountryConfigIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("twende_config_test");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @MockitoBean private KafkaTemplate<String, Object> kafkaTemplate;

    @LocalServerPort private int port;

    @Autowired private CountryConfigRepository countryConfigRepository;
    @Autowired private VehicleTypeConfigRepository vehicleTypeConfigRepository;
    @Autowired private OperatingCityRepository operatingCityRepository;
    @Autowired private PaymentMethodConfigRepository paymentMethodConfigRepository;
    @Autowired private RequiredDriverDocumentRepository requiredDriverDocumentRepository;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

        // Clean up before each test
        requiredDriverDocumentRepository.deleteAll();
        paymentMethodConfigRepository.deleteAll();
        operatingCityRepository.deleteAll();
        vehicleTypeConfigRepository.deleteAll();
        countryConfigRepository.deleteAll();

        // Seed test data
        seedTestData();
    }

    private void seedTestData() {
        CountryConfig tz = new CountryConfig();
        tz.setCode("TZ");
        tz.setName("Tanzania");
        tz.setStatus(CountryStatus.ACTIVE);
        tz.setDefaultLocale("sw-TZ");
        tz.setSupportedLocales(new String[] {"sw-TZ", "en-TZ"});
        tz.setDateFormat("DD/MM/YYYY");
        tz.setDistanceUnit("km");
        tz.setTimeFormat("12h");
        tz.setCurrencyCode("TZS");
        tz.setCurrencySymbol("TSh");
        tz.setMinorUnits(0);
        tz.setDisplayFormat("TSh {amount}");
        tz.setPhonePrefix("+255");
        tz.setCashEnabled(true);
        tz.setSubscriptionAggregator("selcom");
        tz.setSmsProvider("AFRICASTALKING");
        tz.setPushProvider("FCM");
        tz.setRegulatoryAuthority("SUMATRA");
        tz.setTripReportingRequired(true);
        tz.setDataRetentionDays(365);
        tz.setFeatures("{\"ussdEnabled\":true,\"surgeEnabled\":true,\"loyaltyEnabled\":true}");
        countryConfigRepository.save(tz);

        VehicleTypeConfig bajaj = new VehicleTypeConfig();
        bajaj.setCountryCode("TZ");
        bajaj.setVehicleType("BAJAJ");
        bajaj.setDisplayName("Bajaj");
        bajaj.setMaxPassengers(2);
        bajaj.setIsActive(true);
        bajaj.setBaseFare(new BigDecimal("500"));
        bajaj.setPerKm(new BigDecimal("200"));
        bajaj.setPerMinute(new BigDecimal("20"));
        bajaj.setMinimumFare(new BigDecimal("1000"));
        bajaj.setCancellationFee(new BigDecimal("200"));
        bajaj.setSurgeMultiplierCap(new BigDecimal("2.50"));
        bajaj.setRequiredDocs(new String[] {});
        vehicleTypeConfigRepository.save(bajaj);

        VehicleTypeConfig boda = new VehicleTypeConfig();
        boda.setCountryCode("TZ");
        boda.setVehicleType("BODA_BODA");
        boda.setDisplayName("Boda Boda");
        boda.setMaxPassengers(1);
        boda.setIsActive(true);
        boda.setBaseFare(new BigDecimal("300"));
        boda.setPerKm(new BigDecimal("150"));
        boda.setPerMinute(new BigDecimal("15"));
        boda.setMinimumFare(new BigDecimal("700"));
        boda.setCancellationFee(new BigDecimal("150"));
        boda.setSurgeMultiplierCap(new BigDecimal("2.50"));
        boda.setRequiredDocs(new String[] {});
        vehicleTypeConfigRepository.save(boda);

        OperatingCity dar = new OperatingCity();
        dar.setCountryCode("TZ");
        dar.setCityId("dar-es-salaam");
        dar.setName("Dar es Salaam");
        dar.setTimezone("Africa/Dar_es_Salaam");
        dar.setStatus("ACTIVE");
        dar.setCenterLat(-6.7924);
        dar.setCenterLng(39.2083);
        dar.setRadiusKm(30);
        dar.setGeocodingProvider("GOOGLE");
        dar.setRoutingProvider("GOOGLE");
        dar.setAutocompleteProvider("GOOGLE");
        operatingCityRepository.save(dar);

        PaymentMethodConfig mobileMoney = new PaymentMethodConfig();
        mobileMoney.setCountryCode("TZ");
        mobileMoney.setMethodId("mobile_money");
        mobileMoney.setProvider("selcom");
        mobileMoney.setIsEnabled(true);
        mobileMoney.setDisplayName("Pesa ya Simu");
        paymentMethodConfigRepository.save(mobileMoney);

        RequiredDriverDocument nationalId = new RequiredDriverDocument();
        nationalId.setCountryCode("TZ");
        nationalId.setDocumentType("NATIONAL_ID");
        nationalId.setDisplayName("Kitambulisho cha Taifa");
        nationalId.setIsMandatory(true);
        requiredDriverDocumentRepository.save(nationalId);
    }

    @Test
    void givenTestCountryConfig_whenGetConfig_thenReturnsFullDto() {
        webTestClient
                .get()
                .uri("/api/v1/config/TZ")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.code")
                .isEqualTo("TZ")
                .jsonPath("$.data.name")
                .isEqualTo("Tanzania")
                .jsonPath("$.data.status")
                .isEqualTo("ACTIVE")
                .jsonPath("$.data.currencyCode")
                .isEqualTo("TZS")
                .jsonPath("$.data.phonePrefix")
                .isEqualTo("+255")
                .jsonPath("$.data.smsProvider")
                .isEqualTo("AFRICASTALKING")
                .jsonPath("$.data.pushProvider")
                .isEqualTo("FCM")
                .jsonPath("$.data.vehicleTypes")
                .isArray()
                .jsonPath("$.data.vehicleTypes.length()")
                .isEqualTo(2)
                .jsonPath("$.data.cities")
                .isArray()
                .jsonPath("$.data.cities.length()")
                .isEqualTo(1)
                .jsonPath("$.data.paymentMethods")
                .isArray()
                .jsonPath("$.data.paymentMethods.length()")
                .isEqualTo(1)
                .jsonPath("$.data.requiredDocuments")
                .isArray()
                .jsonPath("$.data.requiredDocuments.length()")
                .isEqualTo(1);
    }

    @Test
    void givenActiveCountry_whenGetActive_thenReturnsCode() {
        webTestClient
                .get()
                .uri("/api/v1/config/active")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data")
                .isArray()
                .jsonPath("$.data[0]")
                .isEqualTo("TZ");
    }

    @Test
    void givenVehicleTypes_whenGetByCountry_thenReturnsList() {
        webTestClient
                .get()
                .uri("/api/v1/config/TZ/vehicle-types")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data")
                .isArray()
                .jsonPath("$.data.length()")
                .isEqualTo(2);
    }

    @Test
    void givenAdminHeader_whenUpdateFeatures_thenUpdatesSuccessfully() {
        webTestClient
                .patch()
                .uri("/api/v1/config/TZ/features")
                .header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("surgeEnabled", false, "loyaltyEnabled", true))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.message")
                .isEqualTo("Features updated");

        // Verify the update persisted
        CountryConfig updated = countryConfigRepository.findById("TZ").orElseThrow();
        assertThat(updated.getFeatures()).contains("surgeEnabled");
        assertThat(updated.getFeatures()).contains("false");
    }

    @Test
    void givenNoAdminHeader_whenUpdateConfig_thenReturns401() {
        webTestClient
                .put()
                .uri("/api/v1/config/TZ")
                .header("X-User-Role", "RIDER")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "Updated Tanzania"))
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(false)
                .jsonPath("$.message")
                .isEqualTo("Admin access required");
    }

    @Test
    void givenNonexistentCountry_whenGetConfig_thenReturns404() {
        webTestClient
                .get()
                .uri("/api/v1/config/XX")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(false)
                .jsonPath("$.message")
                .value(msg -> assertThat((String) msg).contains("Country not found"));
    }

    @Test
    void givenAdminHeader_whenUpdateConfig_thenUpdatesSuccessfully() {
        webTestClient
                .put()
                .uri("/api/v1/config/TZ")
                .header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "United Republic of Tanzania"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true);

        CountryConfig updated = countryConfigRepository.findById("TZ").orElseThrow();
        assertThat(updated.getName()).isEqualTo("United Republic of Tanzania");
    }

    @Test
    void givenAdminHeader_whenCreateCity_thenReturns201() {
        webTestClient
                .post()
                .uri("/api/v1/config/TZ/cities")
                .header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                        Map.of(
                                "cityId",
                                "mwanza",
                                "name",
                                "Mwanza",
                                "timezone",
                                "Africa/Dar_es_Salaam",
                                "centerLat",
                                -2.5164,
                                "centerLng",
                                32.9175,
                                "radiusKm",
                                15))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.cityId")
                .isEqualTo("mwanza")
                .jsonPath("$.data.name")
                .isEqualTo("Mwanza");
    }

    @Test
    void givenCities_whenGetCities_thenReturnsList() {
        webTestClient
                .get()
                .uri("/api/v1/config/TZ/cities")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data")
                .isArray()
                .jsonPath("$.data.length()")
                .isEqualTo(1)
                .jsonPath("$.data[0].cityId")
                .isEqualTo("dar-es-salaam");
    }

    @Test
    void givenAdminHeader_whenGetAllConfigs_thenReturnsAll() {
        webTestClient
                .get()
                .uri("/api/v1/config/admin")
                .header("X-User-Role", "ADMIN")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data")
                .isArray()
                .jsonPath("$.data.length()")
                .isEqualTo(1)
                .jsonPath("$.data[0].code")
                .isEqualTo("TZ");
    }

    @Test
    void givenNoAdminHeader_whenGetAllConfigs_thenReturns401() {
        webTestClient
                .get()
                .uri("/api/v1/config/admin")
                .header("X-User-Role", "RIDER")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
}
