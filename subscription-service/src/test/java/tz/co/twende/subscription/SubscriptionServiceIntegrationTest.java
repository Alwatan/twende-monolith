package tz.co.twende.subscription;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import tz.co.twende.subscription.client.PaymentClient;
import tz.co.twende.subscription.repository.SubscriptionPlanRepository;
import tz.co.twende.subscription.repository.SubscriptionRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class SubscriptionServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("twende_subscriptions_test")
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

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionPlanRepository planRepository;

    @MockitoBean private KafkaTemplate<String, Object> kafkaTemplate;
    @MockitoBean private PaymentClient paymentClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        subscriptionRepository.deleteAll();
        planRepository.deleteAll();
    }

    @Test
    void givenPlansExist_whenGetPlans_thenReturn200WithPlans() {
        UUID planId = UUID.randomUUID();
        insertPlan(planId, "TZ", "BAJAJ", "DAILY", BigDecimal.valueOf(2000), "TZS", 24);

        webTestClient
                .get()
                .uri("/api/v1/subscriptions/plans?vehicleType=BAJAJ")
                .header("X-Country-Code", "TZ")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.length()")
                .isEqualTo(1)
                .jsonPath("$.data[0].vehicleType")
                .isEqualTo("BAJAJ");
    }

    @Test
    void givenNoPlansForVehicleType_whenGetPlans_thenReturnEmptyList() {
        UUID planId = UUID.randomUUID();
        insertPlan(planId, "TZ", "BAJAJ", "DAILY", BigDecimal.valueOf(2000), "TZS", 24);

        webTestClient
                .get()
                .uri("/api/v1/subscriptions/plans?vehicleType=BODA_BODA")
                .header("X-Country-Code", "TZ")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.length()")
                .isEqualTo(0);
    }

    @Test
    void givenNoSubscription_whenGetCurrentSubscription_thenReturnNullData() {
        UUID driverId = UUID.randomUUID();

        webTestClient
                .get()
                .uri("/api/v1/subscriptions/me")
                .header("X-User-Id", driverId.toString())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data")
                .isEmpty();
    }

    @Test
    void givenDriverId_whenCheckInternalActive_thenReturnsFalse() {
        UUID driverId = UUID.randomUUID();

        webTestClient
                .get()
                .uri("/internal/subscriptions/{driverId}/active", driverId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data")
                .isEqualTo(false);
    }

    @Test
    void givenActiveSubscription_whenCheckInternalActive_thenReturnsTrue() {
        UUID driverId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        insertPlan(planId, "TZ", "BAJAJ", "DAILY", BigDecimal.valueOf(2000), "TZS", 24);
        insertSubscription(
                driverId,
                planId,
                "TZ",
                "ACTIVE",
                Instant.now(),
                Instant.now().plus(24, ChronoUnit.HOURS));

        webTestClient
                .get()
                .uri("/internal/subscriptions/{driverId}/active", driverId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data")
                .isEqualTo(true);
    }

    @Test
    void givenExpiredSubscription_whenCheckInternalActive_thenReturnsFalse() {
        UUID driverId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        insertPlan(planId, "TZ", "BAJAJ", "DAILY", BigDecimal.valueOf(2000), "TZS", 24);
        insertSubscription(
                driverId,
                planId,
                "TZ",
                "ACTIVE",
                Instant.now().minus(48, ChronoUnit.HOURS),
                Instant.now().minus(24, ChronoUnit.HOURS));

        webTestClient
                .get()
                .uri("/internal/subscriptions/{driverId}/active", driverId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data")
                .isEqualTo(false);
    }

    @Test
    void givenSubscriptionHistory_whenGetHistory_thenReturnPaginatedResults() {
        UUID driverId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        insertPlan(planId, "TZ", "BAJAJ", "DAILY", BigDecimal.valueOf(2000), "TZS", 24);
        insertSubscription(
                driverId,
                planId,
                "TZ",
                "EXPIRED",
                Instant.now().minus(48, ChronoUnit.HOURS),
                Instant.now().minus(24, ChronoUnit.HOURS));

        webTestClient
                .get()
                .uri("/api/v1/subscriptions/me/history?page=0&size=10")
                .header("X-User-Id", driverId.toString())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.content.length()")
                .isEqualTo(1);
    }

    private void insertPlan(
            UUID id,
            String countryCode,
            String vehicleType,
            String planType,
            BigDecimal price,
            String currencyCode,
            int durationHours) {
        jdbcTemplate.update(
                "INSERT INTO subscription_plans (id, country_code, vehicle_type, plan_type,"
                        + " price, currency_code, duration_hours, is_active, display_name,"
                        + " created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, true, ?, now(), now())",
                id,
                countryCode,
                vehicleType,
                planType,
                price,
                currencyCode,
                durationHours,
                vehicleType + " - " + planType);
    }

    private void insertSubscription(
            UUID driverId,
            UUID planId,
            String countryCode,
            String status,
            Instant startedAt,
            Instant expiresAt) {
        jdbcTemplate.update(
                "INSERT INTO subscriptions (id, driver_id, country_code, plan_id,"
                        + " status, payment_method, amount_paid, started_at, expires_at,"
                        + " created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, 'MOBILE_MONEY', 2000, ?, ?, now(), now())",
                UUID.randomUUID(),
                driverId,
                countryCode,
                planId,
                status,
                java.sql.Timestamp.from(startedAt),
                java.sql.Timestamp.from(expiresAt));
    }
}
