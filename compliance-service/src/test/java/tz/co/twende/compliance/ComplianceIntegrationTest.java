package tz.co.twende.compliance;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
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
import tz.co.twende.compliance.repository.AuditLogRepository;
import tz.co.twende.compliance.repository.TripReportRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class ComplianceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

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

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TripReportRepository tripReportRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        jdbcTemplate.execute("DELETE FROM trip_reports");
        jdbcTemplate.execute("DELETE FROM audit_log");
    }

    @Test
    void givenAdminRole_whenGetReports_thenReturnsOk() {
        webTestClient
                .get()
                .uri("/api/v1/compliance/reports")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.content")
                .isArray();
    }

    @Test
    void givenNonAdminRole_whenGetReports_thenReturns401() {
        webTestClient
                .get()
                .uri("/api/v1/compliance/reports")
                .header("X-User-Role", "RIDER")
                .exchange()
                .expectStatus()
                .isEqualTo(401);
    }

    @Test
    void givenExistingReport_whenGetReportById_thenReturnsReport() {
        UUID id = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcTemplate.update(
                "INSERT INTO trip_reports (id, country_code, ride_id, driver_id, rider_id, vehicle_type, "
                        + "pickup_lat, pickup_lng, dropoff_lat, dropoff_lng, fare, currency, submitted, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false, ?, ?)",
                id,
                "TZ",
                rideId,
                driverId,
                riderId,
                "BAJAJ",
                new BigDecimal("-6.7728"),
                new BigDecimal("39.2310"),
                new BigDecimal("-6.8160"),
                new BigDecimal("39.2803"),
                new BigDecimal("3500.00"),
                "TZS",
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now));

        webTestClient
                .get()
                .uri("/api/v1/compliance/reports/{id}", id)
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.rideId")
                .isEqualTo(rideId.toString())
                .jsonPath("$.data.vehicleType")
                .isEqualTo("BAJAJ");
    }

    @Test
    void givenReports_whenGetStats_thenReturnsStatistics() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcTemplate.update(
                "INSERT INTO trip_reports (id, country_code, ride_id, driver_id, rider_id, vehicle_type, "
                        + "pickup_lat, pickup_lng, dropoff_lat, dropoff_lng, fare, submitted, created_at, updated_at) "
                        + "VALUES (?, 'TZ', ?, ?, ?, 'BAJAJ', -6.77, 39.23, -6.81, 39.28, 3500, false, ?, ?)",
                id,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now));

        webTestClient
                .get()
                .uri("/api/v1/compliance/reports/stats")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data[0].countryCode")
                .isEqualTo("TZ")
                .jsonPath("$.data[0].pending")
                .isEqualTo(1);
    }

    @Test
    void givenAdminRole_whenGetAuditLog_thenReturnsOk() {
        webTestClient
                .get()
                .uri("/api/v1/compliance/audit-log")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true);
    }

    @Test
    void givenNonAdminRole_whenGetAuditLog_thenReturns401() {
        webTestClient
                .get()
                .uri("/api/v1/compliance/audit-log")
                .header("X-User-Role", "DRIVER")
                .exchange()
                .expectStatus()
                .isEqualTo(401);
    }

    @Test
    void givenAdminRole_whenRetrySubmissions_thenReturnsProcessedCount() {
        webTestClient
                .post()
                .uri("/api/v1/compliance/reports/retry?countryCode=TZ")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data")
                .isEqualTo(0);
    }
}
