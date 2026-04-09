package tz.co.twende.analytics;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import tz.co.twende.analytics.repository.AnalyticsEventRepository;
import tz.co.twende.analytics.repository.DriverDailySummaryRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class AnalyticsServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("twende_analytics_test")
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

    @Autowired private AnalyticsEventRepository eventRepository;
    @Autowired private DriverDailySummaryRepository summaryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        summaryRepository.deleteAll();
        eventRepository.deleteAll();
    }

    @Test
    void givenDriverSummaries_whenGetEarnings_thenReturn200WithAggregatedData() {
        UUID driverId = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        insertSummary(
                driverId,
                "TZ",
                today.minusDays(1),
                5,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4));
        insertSummary(
                driverId,
                "TZ",
                today.minusDays(2),
                3,
                BigDecimal.valueOf(6000),
                BigDecimal.valueOf(3));

        webTestClient
                .get()
                .uri("/api/v1/analytics/driver/earnings?period=WEEKLY")
                .header("X-User-Id", driverId.toString())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.driverId")
                .isEqualTo(driverId.toString())
                .jsonPath("$.data.tripCount")
                .isEqualTo(8)
                .jsonPath("$.data.dailyBreakdown.length()")
                .isEqualTo(2);
    }

    @Test
    void givenDriverSummaries_whenGetTripStats_thenReturn200() {
        UUID driverId = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        insertSummary(
                driverId,
                "TZ",
                today.minusDays(1),
                10,
                BigDecimal.valueOf(20000),
                BigDecimal.valueOf(8));

        webTestClient
                .get()
                .uri("/api/v1/analytics/driver/trips")
                .header("X-User-Id", driverId.toString())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.totalTrips")
                .isEqualTo(10);
    }

    @Test
    void givenAdminRole_whenGetOverview_thenReturn200() {
        // Insert some analytics events
        insertEvent(
                "RIDE_COMPLETED", "TZ", UUID.randomUUID(), Instant.now(), "{\"finalFare\": 5000}");
        insertEvent(
                "USER_REGISTERED",
                "TZ",
                UUID.randomUUID(),
                Instant.now(),
                "{\"userId\": \"" + UUID.randomUUID() + "\"}");

        webTestClient
                .get()
                .uri("/api/v1/analytics/admin/overview")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.currencyCode")
                .isEqualTo("TZS");
    }

    @Test
    void givenNonAdminRole_whenGetOverview_thenReturn401() {
        webTestClient
                .get()
                .uri("/api/v1/analytics/admin/overview")
                .header("X-User-Role", "DRIVER")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void givenAdminRole_whenGetCountryMetrics_thenReturn200() {
        insertEvent(
                "RIDE_COMPLETED",
                "TZ",
                UUID.randomUUID(),
                Instant.now(),
                "{\"finalFare\": 3000, \"vehicleType\": \"BAJAJ\"}");

        webTestClient
                .get()
                .uri("/api/v1/analytics/admin/countries/TZ")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.countryCode")
                .isEqualTo("TZ");
    }

    @Test
    void givenHealthEndpoint_whenGet_thenReturn200() {
        webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    private void insertSummary(
            UUID driverId,
            String countryCode,
            LocalDate date,
            int tripCount,
            BigDecimal totalEarned,
            BigDecimal onlineHours) {
        jdbcTemplate.update(
                "INSERT INTO driver_daily_summaries "
                        + "(id, driver_id, country_code, date, trip_count, total_earned,"
                        + " online_hours, created_at, updated_at) "
                        + "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, now(), now())",
                driverId,
                countryCode,
                date,
                tripCount,
                totalEarned,
                onlineHours);
    }

    private void insertEvent(
            String eventType,
            String countryCode,
            UUID actorId,
            Instant occurredAt,
            String payload) {
        jdbcTemplate.update(
                "INSERT INTO analytics_events "
                        + "(id, event_type, country_code, actor_id, payload, occurred_at,"
                        + " created_at, updated_at) "
                        + "VALUES (gen_random_uuid(), ?, ?, ?, ?::jsonb, ?, now(), now())",
                eventType,
                countryCode,
                actorId,
                payload,
                java.sql.Timestamp.from(occurredAt));
    }
}
