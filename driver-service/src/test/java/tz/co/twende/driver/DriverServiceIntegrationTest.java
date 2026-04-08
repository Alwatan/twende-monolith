package tz.co.twende.driver;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
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
import tz.co.twende.driver.client.LocationClient;
import tz.co.twende.driver.client.SubscriptionClient;
import tz.co.twende.driver.repository.DriverDocumentRepository;
import tz.co.twende.driver.repository.DriverProfileRepository;
import tz.co.twende.driver.repository.DriverStatusLogRepository;
import tz.co.twende.driver.repository.DriverVehicleRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class DriverServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("twende_drivers_test")
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

    @Autowired private DriverProfileRepository driverProfileRepository;
    @Autowired private DriverVehicleRepository vehicleRepository;
    @Autowired private DriverDocumentRepository documentRepository;
    @Autowired private DriverStatusLogRepository statusLogRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private KafkaTemplate<String, Object> kafkaTemplate;
    @MockitoBean private SubscriptionClient subscriptionClient;
    @MockitoBean private LocationClient locationClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        statusLogRepository.deleteAll();
        documentRepository.deleteAll();
        vehicleRepository.deleteAll();
        driverProfileRepository.deleteAll();
    }

    @Test
    void givenExistingDriver_whenGetProfile_thenReturn200() {
        UUID driverId = UUID.randomUUID();
        createDriver(driverId, "John Driver", "TZ", "PENDING_APPROVAL");

        webTestClient
                .get()
                .uri("/api/v1/drivers/me")
                .header("X-User-Id", driverId.toString())
                .header("X-User-Role", "DRIVER")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.fullName")
                .isEqualTo("John Driver");
    }

    @Test
    void givenNonExistingDriver_whenGetProfile_thenReturn404() {
        UUID driverId = UUID.randomUUID();

        webTestClient
                .get()
                .uri("/api/v1/drivers/me")
                .header("X-User-Id", driverId.toString())
                .header("X-User-Role", "DRIVER")
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void givenExistingDriver_whenUpdateProfile_thenReturnUpdated() {
        UUID driverId = UUID.randomUUID();
        createDriver(driverId, "John Driver", "TZ", "APPROVED");

        webTestClient
                .put()
                .uri("/api/v1/drivers/me")
                .header("X-User-Id", driverId.toString())
                .header("X-User-Role", "DRIVER")
                .bodyValue(Map.of("fullName", "John Updated", "email", "john@test.com"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.fullName")
                .isEqualTo("John Updated")
                .jsonPath("$.data.email")
                .isEqualTo("john@test.com");
    }

    @Test
    void givenDriver_whenRegisterVehicle_thenReturn201() {
        UUID driverId = UUID.randomUUID();
        createDriver(driverId, "John Driver", "TZ", "APPROVED");

        webTestClient
                .post()
                .uri("/api/v1/drivers/me/vehicles")
                .header("X-User-Id", driverId.toString())
                .header("X-User-Role", "DRIVER")
                .header("X-Country-Code", "TZ")
                .bodyValue(
                        Map.of(
                                "vehicleType",
                                "BAJAJ",
                                "plateNumber",
                                "T123ABC",
                                "make",
                                "Bajaj",
                                "color",
                                "Yellow"))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.data.vehicleType")
                .isEqualTo("BAJAJ")
                .jsonPath("$.data.plateNumber")
                .isEqualTo("T123ABC");
    }

    @Test
    void givenDriver_whenUploadDocument_thenReturn201() {
        UUID driverId = UUID.randomUUID();
        createDriver(driverId, "John Driver", "TZ", "PENDING_APPROVAL");

        webTestClient
                .post()
                .uri("/api/v1/drivers/me/documents")
                .header("X-User-Id", driverId.toString())
                .header("X-User-Role", "DRIVER")
                .header("X-Country-Code", "TZ")
                .bodyValue(
                        Map.of(
                                "documentType",
                                "NATIONAL_ID",
                                "fileUrl",
                                "http://minio/docs/id.pdf"))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.data.documentType")
                .isEqualTo("NATIONAL_ID")
                .jsonPath("$.data.status")
                .isEqualTo("PENDING");
    }

    @Test
    void givenAdmin_whenListDrivers_thenReturn200() {
        UUID driverId = UUID.randomUUID();
        createDriver(driverId, "John Driver", "TZ", "PENDING_APPROVAL");

        webTestClient
                .get()
                .uri("/api/v1/drivers")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.content.length()")
                .isEqualTo(1);
    }

    @Test
    void givenAdmin_whenGetDriverDetail_thenReturn200() {
        UUID driverId = UUID.randomUUID();
        createDriver(driverId, "John Driver", "TZ", "PENDING_APPROVAL");

        webTestClient
                .get()
                .uri("/api/v1/drivers/{id}", driverId)
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.profile.fullName")
                .isEqualTo("John Driver")
                .jsonPath("$.data.vehicles.length()")
                .isEqualTo(0)
                .jsonPath("$.data.documents.length()")
                .isEqualTo(0);
    }

    @Test
    void givenAdmin_whenApproveDriver_thenStatusChangesToApproved() {
        UUID driverId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        createDriver(driverId, "John Driver", "TZ", "PENDING_APPROVAL");

        webTestClient
                .put()
                .uri("/api/v1/drivers/{id}/approval", driverId)
                .header("X-User-Id", adminId.toString())
                .header("X-User-Role", "ADMIN")
                .bodyValue(Map.of("approved", true))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.status")
                .isEqualTo("APPROVED");
    }

    @Test
    void givenInternalEndpoint_whenGetDriver_thenReturn200() {
        UUID driverId = UUID.randomUUID();
        createDriver(driverId, "John Driver", "TZ", "APPROVED");

        webTestClient
                .get()
                .uri("/internal/drivers/{id}", driverId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.fullName")
                .isEqualTo("John Driver");
    }

    @Test
    void givenRiderRole_whenAccessDriverEndpoint_thenReturn401() {
        UUID userId = UUID.randomUUID();

        webTestClient
                .get()
                .uri("/api/v1/drivers/me")
                .header("X-User-Id", userId.toString())
                .header("X-User-Role", "RIDER")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void givenDriverRole_whenAccessAdminEndpoint_thenReturn401() {
        webTestClient
                .get()
                .uri("/api/v1/drivers")
                .header("X-User-Role", "DRIVER")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    private void createDriver(UUID driverId, String fullName, String countryCode, String status) {
        jdbcTemplate.update(
                "INSERT INTO drivers (id, full_name, country_code, status,"
                        + " trip_count, revenue_model, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, 0, 'SUBSCRIPTION', now(), now())",
                driverId,
                fullName,
                countryCode,
                status);
    }
}
