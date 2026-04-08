package tz.co.twende.location;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
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
import org.testcontainers.utility.DockerImageName;
import tz.co.twende.location.client.CountryConfigClient;
import tz.co.twende.location.repository.GeocodeCacheRepository;
import tz.co.twende.location.repository.TripTraceRepository;
import tz.co.twende.location.repository.ZoneRepository;
import tz.co.twende.location.service.LocationService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class LocationServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("twende_locations_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withInitScript("init-postgis.sql");

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

    @BeforeAll
    static void initPostgis() throws Exception {
        postgres.execInContainer(
                "psql",
                "-U",
                "test",
                "-d",
                "twende_locations_test",
                "-c",
                "CREATE EXTENSION IF NOT EXISTS postgis;");
    }

    @org.springframework.boot.test.web.server.LocalServerPort private int port;

    private WebTestClient webTestClient;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private GeocodeCacheRepository geocodeCacheRepository;
    @Autowired private TripTraceRepository tripTraceRepository;
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private LocationService locationService;

    @MockitoBean private KafkaTemplate<String, Object> kafkaTemplate;
    @MockitoBean private CountryConfigClient countryConfigClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        tripTraceRepository.deleteAll();
        geocodeCacheRepository.deleteAll();
        zoneRepository.deleteAll();
    }

    @Test
    void givenZoneExists_whenCheckZones_thenReturn200WithZones() {
        UUID cityId = UUID.randomUUID();
        insertZone(
                cityId,
                "Dar es Salaam Operating",
                "POLYGON((39.1 -6.9, 39.4 -6.9, 39.4 -6.6, 39.1 -6.6, 39.1 -6.9))",
                "OPERATING",
                "TZ");

        webTestClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/api/v1/locations/zones/check")
                                        .queryParam("lat", "-6.7924")
                                        .queryParam("lng", "39.2083")
                                        .queryParam("cityId", cityId)
                                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.inServiceArea")
                .isEqualTo(true);
    }

    @Test
    void givenNoZones_whenCheckZones_thenReturnNotInServiceArea() {
        UUID cityId = UUID.randomUUID();

        webTestClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/api/v1/locations/zones/check")
                                        .queryParam("lat", "-6.7924")
                                        .queryParam("lng", "39.2083")
                                        .queryParam("cityId", cityId)
                                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.inServiceArea")
                .isEqualTo(false);
    }

    @Test
    void givenRestrictedZone_whenCheckZones_thenReturnRestricted() {
        UUID cityId = UUID.randomUUID();
        insertZone(
                cityId,
                "Government Area",
                "POLYGON((39.2 -6.8, 39.3 -6.8, 39.3 -6.7, 39.2 -6.7, 39.2 -6.8))",
                "RESTRICTED",
                "TZ");

        webTestClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/api/v1/locations/zones/check")
                                        .queryParam("lat", "-6.75")
                                        .queryParam("lng", "39.25")
                                        .queryParam("cityId", cityId)
                                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.restricted")
                .isEqualTo(true);
    }

    @Test
    void givenCityId_whenListZones_thenReturnAllZones() {
        UUID cityId = UUID.randomUUID();
        insertZone(
                cityId,
                "Zone A",
                "POLYGON((39.1 -6.9, 39.4 -6.9, 39.4 -6.6, 39.1 -6.6, 39.1 -6.9))",
                "OPERATING",
                "TZ");
        insertZone(
                cityId,
                "Zone B",
                "POLYGON((39.2 -6.8, 39.3 -6.8, 39.3 -6.7, 39.2 -6.7, 39.2 -6.8))",
                "SURGE",
                "TZ");

        webTestClient
                .get()
                .uri("/api/v1/locations/cities/{cityId}/zones", cityId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.length()")
                .isEqualTo(2);
    }

    @Test
    void givenAdminRole_whenCreateZone_thenReturn201() {
        UUID cityId = UUID.randomUUID();

        webTestClient
                .post()
                .uri("/api/v1/locations/cities/{cityId}/zones", cityId)
                .header("X-User-Role", "ADMIN")
                .header("X-Country-Code", "TZ")
                .bodyValue(
                        Map.of(
                                "name", "Test Zone",
                                "boundary",
                                        "POLYGON((39.1 -6.9, 39.4 -6.9, 39.4 -6.6, 39.1 -6.6, 39.1 -6.9))",
                                "type", "OPERATING"))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.data.name")
                .isEqualTo("Test Zone")
                .jsonPath("$.data.type")
                .isEqualTo("OPERATING");
    }

    @Test
    void givenNonAdminRole_whenCreateZone_thenReturn401() {
        UUID cityId = UUID.randomUUID();

        webTestClient
                .post()
                .uri("/api/v1/locations/cities/{cityId}/zones", cityId)
                .header("X-User-Role", "RIDER")
                .header("X-Country-Code", "TZ")
                .bodyValue(
                        Map.of(
                                "name", "Test",
                                "boundary", "POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))",
                                "type", "OPERATING"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void givenDriverLocation_whenQueryInternalNearby_thenReturnResults() {
        locationService.updateDriverLocation(
                UUID.randomUUID(),
                "TZ",
                "BAJAJ",
                new BigDecimal("-6.7924"),
                new BigDecimal("39.2083"),
                45,
                30);

        webTestClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/internal/location/drivers/nearby")
                                        .queryParam("countryCode", "TZ")
                                        .queryParam("vehicleType", "BAJAJ")
                                        .queryParam("lat", "-6.7924")
                                        .queryParam("lng", "39.2083")
                                        .queryParam("radiusKm", "5")
                                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true);
    }

    @Test
    void givenDriverId_whenQueryInternalDriverLocation_thenReturnLocation() {
        UUID driverId = UUID.randomUUID();
        locationService.updateDriverLocation(
                driverId,
                "TZ",
                "BAJAJ",
                new BigDecimal("-6.7924"),
                new BigDecimal("39.2083"),
                90,
                20);

        webTestClient
                .get()
                .uri("/internal/location/driver/{driverId}", driverId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.driverId")
                .isEqualTo(driverId.toString());
    }

    private void insertZone(
            UUID cityId, String name, String boundary, String type, String countryCode) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO zones (id, city_id, name, boundary, type, config, is_active,"
                        + " country_code, created_at, updated_at) VALUES"
                        + " (?, ?, ?, ST_GeogFromText(?), ?, '{}', true, ?, now(), now())",
                id,
                cityId,
                name,
                boundary,
                type,
                countryCode);
    }
}
