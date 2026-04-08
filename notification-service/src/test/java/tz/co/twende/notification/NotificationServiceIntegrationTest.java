package tz.co.twende.notification;

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
import tz.co.twende.notification.client.CountryConfigClient;
import tz.co.twende.notification.repository.FcmTokenRepository;
import tz.co.twende.notification.repository.NotificationLogRepository;
import tz.co.twende.notification.repository.NotificationTemplateRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class NotificationServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("twende_notifications_test")
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

    @Autowired private FcmTokenRepository fcmTokenRepository;
    @Autowired private NotificationLogRepository notificationLogRepository;
    @Autowired private NotificationTemplateRepository templateRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private KafkaTemplate<String, Object> kafkaTemplate;
    @MockitoBean private CountryConfigClient countryConfigClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        notificationLogRepository.deleteAll();
        fcmTokenRepository.deleteAll();
    }

    @Test
    void givenValidRequest_whenRegisterFcmToken_thenReturn200() {
        UUID userId = UUID.randomUUID();

        webTestClient
                .post()
                .uri("/api/v1/notifications/fcm-token")
                .header("X-User-Id", userId.toString())
                .header("X-Country-Code", "TZ")
                .bodyValue(Map.of("token", "fcm-token-123", "platform", "ANDROID"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.token")
                .isEqualTo("fcm-token-123")
                .jsonPath("$.data.platform")
                .isEqualTo("ANDROID")
                .jsonPath("$.data.active")
                .isEqualTo(true);
    }

    @Test
    void givenExistingToken_whenRegisterSameToken_thenUpserted() {
        UUID userId = UUID.randomUUID();

        // First registration
        webTestClient
                .post()
                .uri("/api/v1/notifications/fcm-token")
                .header("X-User-Id", userId.toString())
                .header("X-Country-Code", "TZ")
                .bodyValue(Map.of("token", "fcm-token-456", "platform", "ANDROID"))
                .exchange()
                .expectStatus()
                .isOk();

        // Second registration with same token
        webTestClient
                .post()
                .uri("/api/v1/notifications/fcm-token")
                .header("X-User-Id", userId.toString())
                .header("X-Country-Code", "TZ")
                .bodyValue(Map.of("token", "fcm-token-456", "platform", "IOS"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.platform")
                .isEqualTo("IOS");

        // Only one token should exist
        assertThat(fcmTokenRepository.findByUserIdAndToken(userId, "fcm-token-456")).isPresent();
    }

    @Test
    void givenMissingToken_whenRegisterFcmToken_thenReturn400() {
        UUID userId = UUID.randomUUID();

        webTestClient
                .post()
                .uri("/api/v1/notifications/fcm-token")
                .header("X-User-Id", userId.toString())
                .header("X-Country-Code", "TZ")
                .bodyValue(Map.of("platform", "ANDROID"))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void givenMissingPlatform_whenRegisterFcmToken_thenReturn400() {
        UUID userId = UUID.randomUUID();

        webTestClient
                .post()
                .uri("/api/v1/notifications/fcm-token")
                .header("X-User-Id", userId.toString())
                .header("X-Country-Code", "TZ")
                .bodyValue(Map.of("token", "fcm-token-789"))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void givenHealthEndpoint_whenAccessed_thenReturn200() {
        webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }
}
