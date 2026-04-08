package tz.co.twende.pricing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import tz.co.twende.pricing.client.CountryConfigClient;
import tz.co.twende.pricing.client.LocationServiceClient;
import tz.co.twende.pricing.dto.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class PricingServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("twende_pricing_test")
                    .withUsername("test")
                    .withPassword("test");

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

    @LocalServerPort private int port;

    private WebTestClient webTestClient;

    @MockitoBean private KafkaTemplate<String, Object> kafkaTemplate;
    @MockitoBean private CountryConfigClient countryConfigClient;
    @MockitoBean private LocationServiceClient locationServiceClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void givenValidEstimateRequest_whenPostEstimate_thenReturn200WithFare() {
        VehicleTypeConfigDto config =
                VehicleTypeConfigDto.builder()
                        .vehicleType("BAJAJ")
                        .baseFare(new BigDecimal("500"))
                        .perKm(new BigDecimal("200"))
                        .perMinute(new BigDecimal("20"))
                        .minimumFare(new BigDecimal("1000"))
                        .cancellationFee(new BigDecimal("200"))
                        .surgeMultiplierCap(new BigDecimal("2.5"))
                        .build();

        RouteDto route = RouteDto.builder().distanceMetres(8200).durationSeconds(900).build();

        ZoneCheckDto noZones =
                ZoneCheckDto.builder()
                        .inServiceArea(true)
                        .restricted(false)
                        .zones(Collections.emptyList())
                        .build();

        when(countryConfigClient.getVehicleTypeConfig("TZ", "BAJAJ")).thenReturn(config);
        when(locationServiceClient.getRoute(any(), any(), any(), any(), any())).thenReturn(route);
        when(locationServiceClient.checkZones(any(), any(), any())).thenReturn(noZones);

        Map<String, Object> request =
                Map.of(
                        "vehicleType", "BAJAJ",
                        "countryCode", "TZ",
                        "pickupLat", -6.7728,
                        "pickupLng", 39.2310,
                        "dropoffLat", -6.8160,
                        "dropoffLng", 39.2803,
                        "cityId", UUID.randomUUID().toString());

        webTestClient
                .post()
                .uri("/api/v1/pricing/estimate")
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.estimatedFare")
                .isEqualTo(2440)
                .jsonPath("$.data.currency")
                .isEqualTo("TZS")
                .jsonPath("$.data.fareBreakdown.baseFare")
                .isEqualTo(500)
                .jsonPath("$.data.fareBreakdown.minimumFareApplied")
                .isEqualTo(false);
    }

    @Test
    void givenSurgeEndpoint_whenGetSurge_thenReturn200() {
        webTestClient
                .get()
                .uri("/api/v1/pricing/surge/TZ/BAJAJ")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.vehicleType")
                .isEqualTo("BAJAJ")
                .jsonPath("$.data.countryCode")
                .isEqualTo("TZ")
                .jsonPath("$.data.surgeMultiplier")
                .isEqualTo(1.0);
    }

    @Test
    void givenInvalidEstimateRequest_whenPostEstimate_thenReturn400() {
        Map<String, Object> request =
                Map.of(
                        "pickupLat", -6.7728,
                        "pickupLng", 39.2310);

        webTestClient
                .post()
                .uri("/api/v1/pricing/estimate")
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void givenValidCalculateRequest_whenPostCalculate_thenReturn200WithFinalFare() {
        VehicleTypeConfigDto config =
                VehicleTypeConfigDto.builder()
                        .vehicleType("BAJAJ")
                        .baseFare(new BigDecimal("500"))
                        .perKm(new BigDecimal("200"))
                        .perMinute(new BigDecimal("20"))
                        .minimumFare(new BigDecimal("1000"))
                        .cancellationFee(new BigDecimal("200"))
                        .surgeMultiplierCap(new BigDecimal("2.5"))
                        .build();

        ZoneCheckDto noZones =
                ZoneCheckDto.builder()
                        .inServiceArea(true)
                        .restricted(false)
                        .zones(Collections.emptyList())
                        .build();

        when(countryConfigClient.getVehicleTypeConfig("TZ", "BAJAJ")).thenReturn(config);
        when(locationServiceClient.checkZones(any(), any(), any())).thenReturn(noZones);

        Map<String, Object> request =
                Map.of(
                        "rideId",
                        UUID.randomUUID().toString(),
                        "vehicleType",
                        "BAJAJ",
                        "countryCode",
                        "TZ",
                        "actualDistanceMetres",
                        10000,
                        "actualDurationSeconds",
                        1200,
                        "pickupLat",
                        -6.7728,
                        "pickupLng",
                        39.2310,
                        "dropoffLat",
                        -6.8160,
                        "dropoffLng",
                        39.2803,
                        "cityId",
                        UUID.randomUUID().toString());

        webTestClient
                .post()
                .uri("/internal/pricing/calculate")
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.finalFare")
                .isEqualTo(2900)
                .jsonPath("$.data.currency")
                .isEqualTo("TZS");
    }

    @Test
    void givenRestrictedZone_whenPostEstimate_thenReturn400() {
        ZoneCheckDto restrictedZone =
                ZoneCheckDto.builder()
                        .inServiceArea(true)
                        .restricted(true)
                        .zones(
                                List.of(
                                        ZoneDto.builder()
                                                .type("RESTRICTED")
                                                .config(Map.of("reason", "Military zone"))
                                                .build()))
                        .build();

        when(locationServiceClient.checkZones(any(), any(), any())).thenReturn(restrictedZone);

        Map<String, Object> request =
                Map.of(
                        "vehicleType", "BAJAJ",
                        "countryCode", "TZ",
                        "pickupLat", -6.7728,
                        "pickupLng", 39.2310,
                        "dropoffLat", -6.8160,
                        "dropoffLng", 39.2803,
                        "cityId", UUID.randomUUID().toString());

        webTestClient
                .post()
                .uri("/api/v1/pricing/estimate")
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(false)
                .jsonPath("$.message")
                .isEqualTo("Military zone");
    }
}
