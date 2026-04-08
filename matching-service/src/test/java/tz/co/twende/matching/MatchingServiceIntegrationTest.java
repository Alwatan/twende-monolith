package tz.co.twende.matching;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import tz.co.twende.matching.client.DriverServiceClient;
import tz.co.twende.matching.client.LocationServiceClient;
import tz.co.twende.matching.client.RatingServiceClient;
import tz.co.twende.matching.client.RideServiceClient;
import tz.co.twende.matching.repository.OfferLogRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class MatchingServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("twende_matching_test")
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

    @Autowired private StringRedisTemplate stringRedisTemplate;
    @Autowired private OfferLogRepository offerLogRepository;

    @MockitoBean private KafkaTemplate<String, Object> kafkaTemplate;
    @MockitoBean private LocationServiceClient locationServiceClient;
    @MockitoBean private DriverServiceClient driverServiceClient;
    @MockitoBean private RatingServiceClient ratingServiceClient;
    @MockitoBean private RideServiceClient rideServiceClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        offerLogRepository.deleteAll();
        // Clean redis keys
        var keys = stringRedisTemplate.keys("ride_accepted:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        keys = stringRedisTemplate.keys("driver_rejected:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        keys = stringRedisTemplate.keys("driver_stats:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void givenNoExistingAcceptance_whenAcceptRide_thenReturnsSuccess() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        webTestClient
                .put()
                .uri("/internal/matching/driver-actions/{rideId}/accept", rideId)
                .header("X-User-Id", driverId.toString())
                .header("X-Country-Code", "TZ")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.success")
                .isEqualTo(true)
                .jsonPath("$.data.action")
                .isEqualTo("ACCEPT")
                .jsonPath("$.data.message")
                .isEqualTo("Ride accepted successfully");

        // Verify Redis lock exists
        String lockValue = stringRedisTemplate.opsForValue().get("ride_accepted:" + rideId);
        assertThat(lockValue).isEqualTo(driverId.toString());
    }

    @Test
    void givenExistingAcceptance_whenSecondDriverAccepts_thenReturnsAlreadyAccepted() {
        UUID rideId = UUID.randomUUID();
        UUID driver1 = UUID.randomUUID();
        UUID driver2 = UUID.randomUUID();

        // First driver accepts
        webTestClient
                .put()
                .uri("/internal/matching/driver-actions/{rideId}/accept", rideId)
                .header("X-User-Id", driver1.toString())
                .header("X-Country-Code", "TZ")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.success")
                .isEqualTo(true);

        // Second driver tries to accept
        webTestClient
                .put()
                .uri("/internal/matching/driver-actions/{rideId}/accept", rideId)
                .header("X-User-Id", driver2.toString())
                .header("X-Country-Code", "TZ")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.success")
                .isEqualTo(false)
                .jsonPath("$.data.message")
                .isEqualTo("Ride already accepted by another driver");

        // Verify first driver still holds the lock
        String lockValue = stringRedisTemplate.opsForValue().get("ride_accepted:" + rideId);
        assertThat(lockValue).isEqualTo(driver1.toString());
    }

    @Test
    void givenDriverRejects_whenRejectRide_thenReturnsSuccessAndTracked() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        webTestClient
                .put()
                .uri("/internal/matching/driver-actions/{rideId}/reject", rideId)
                .header("X-User-Id", driverId.toString())
                .header("X-Country-Code", "TZ")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.action")
                .isEqualTo("REJECT")
                .jsonPath("$.data.success")
                .isEqualTo(true);

        // Verify driver is in rejected set
        Boolean isMember =
                stringRedisTemplate
                        .opsForSet()
                        .isMember("driver_rejected:" + rideId, driverId.toString());
        assertThat(isMember).isTrue();
    }

    @Test
    void givenDriverRejects_whenRejectionStats_thenStatsUpdated() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        // Set initial stats
        stringRedisTemplate.opsForHash().put("driver_stats:" + driverId, "offered_count", "5");
        stringRedisTemplate.opsForHash().put("driver_stats:" + driverId, "accepted_count", "3");
        stringRedisTemplate.opsForHash().put("driver_stats:" + driverId, "rejection_count", "1");

        webTestClient
                .put()
                .uri("/internal/matching/driver-actions/{rideId}/reject", rideId)
                .header("X-User-Id", driverId.toString())
                .header("X-Country-Code", "TZ")
                .exchange()
                .expectStatus()
                .isOk();

        // Verify rejection count was incremented
        Object rejCount =
                stringRedisTemplate.opsForHash().get("driver_stats:" + driverId, "rejection_count");
        assertThat(rejCount).isNotNull();
        assertThat(Long.parseLong(rejCount.toString())).isEqualTo(2);
    }
}
