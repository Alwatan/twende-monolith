package tz.co.twende.rating;

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
import tz.co.twende.rating.client.RideClient;
import tz.co.twende.rating.dto.RideDetailsDto;
import tz.co.twende.rating.kafka.RideCompletedConsumer;
import tz.co.twende.rating.repository.RatingRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class RatingServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("twende_ratings_test")
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

    @Autowired private RatingRepository ratingRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RideCompletedConsumer rideCompletedConsumer;

    @MockitoBean private KafkaTemplate<String, Object> kafkaTemplate;
    @MockitoBean private RideClient rideClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        ratingRepository.deleteAll();
    }

    @Test
    void givenCompletedRide_whenSubmitRating_thenReturn201AndStoreRating() {
        UUID riderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        cacheRide(rideId, riderId, driverId);

        webTestClient
                .post()
                .uri("/api/v1/ratings")
                .header("X-User-Id", riderId.toString())
                .header("X-User-Role", "RIDER")
                .bodyValue(Map.of("rideId", rideId.toString(), "score", 5, "comment", "Excellent!"))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.score")
                .isEqualTo(5)
                .jsonPath("$.data.raterRole")
                .isEqualTo("RIDER")
                .jsonPath("$.data.ratedUserId")
                .isEqualTo(driverId.toString());

        assertThat(ratingRepository.count()).isEqualTo(1);
    }

    @Test
    void givenExistingRating_whenDuplicateSubmit_thenReturn409() {
        UUID riderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        cacheRide(rideId, riderId, driverId);
        insertRating(rideId, driverId, riderId, "RIDER", (short) 4);

        webTestClient
                .post()
                .uri("/api/v1/ratings")
                .header("X-User-Id", riderId.toString())
                .header("X-User-Role", "RIDER")
                .bodyValue(Map.of("rideId", rideId.toString(), "score", 5))
                .exchange()
                .expectStatus()
                .isEqualTo(409);
    }

    @Test
    void givenRatings_whenGetDriverSummary_thenReturnAggregate() {
        UUID driverId = UUID.randomUUID();
        UUID rideId1 = UUID.randomUUID();
        UUID rideId2 = UUID.randomUUID();
        UUID rideId3 = UUID.randomUUID();

        insertRating(rideId1, driverId, UUID.randomUUID(), "RIDER", (short) 5);
        insertRating(rideId2, driverId, UUID.randomUUID(), "RIDER", (short) 4);
        insertRating(rideId3, driverId, UUID.randomUUID(), "RIDER", (short) 3);

        webTestClient
                .get()
                .uri("/api/v1/ratings/driver/{driverId}", driverId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.totalRatings")
                .isEqualTo(3)
                .jsonPath("$.data.averageScore")
                .isEqualTo(4.0);
    }

    @Test
    void givenInternalEndpoint_whenGetDriverScore_thenReturnPlainDto() {
        UUID driverId = UUID.randomUUID();
        insertRating(UUID.randomUUID(), driverId, UUID.randomUUID(), "RIDER", (short) 5);
        insertRating(UUID.randomUUID(), driverId, UUID.randomUUID(), "RIDER", (short) 3);

        webTestClient
                .get()
                .uri("/internal/ratings/driver/{driverId}/score", driverId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.driverId")
                .isEqualTo(driverId.toString())
                .jsonPath("$.average")
                .isEqualTo(4.0)
                .jsonPath("$.count")
                .isEqualTo(2);
    }

    @Test
    void givenNoRatings_whenGetInternalScore_thenReturnZero() {
        UUID driverId = UUID.randomUUID();

        webTestClient
                .get()
                .uri("/internal/ratings/driver/{driverId}/score", driverId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.average")
                .isEqualTo(0.0)
                .jsonPath("$.count")
                .isEqualTo(0);
    }

    @Test
    void givenDriver_whenGetMyRating_thenReturnSummary() {
        UUID driverId = UUID.randomUUID();
        insertRating(UUID.randomUUID(), driverId, UUID.randomUUID(), "RIDER", (short) 5);

        webTestClient
                .get()
                .uri("/api/v1/ratings/me")
                .header("X-User-Id", driverId.toString())
                .header("X-User-Role", "DRIVER")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.totalRatings")
                .isEqualTo(1);
    }

    private void cacheRide(UUID rideId, UUID riderId, UUID driverId) {
        RideDetailsDto ride = new RideDetailsDto();
        ride.setId(rideId);
        ride.setRiderId(riderId);
        ride.setDriverId(driverId);
        ride.setStatus("COMPLETED");
        ride.setCountryCode("TZ");
        ride.setCompletedAt(java.time.Instant.now());

        // Simulate receiving the event
        tz.co.twende.common.event.ride.RideCompletedEvent event =
                new tz.co.twende.common.event.ride.RideCompletedEvent();
        event.setRideId(rideId);
        event.setRiderId(riderId);
        event.setDriverId(driverId);
        event.setCountryCode("TZ");
        event.setCompletedAt(java.time.Instant.now());
        rideCompletedConsumer.onRideCompleted(event);
    }

    private void insertRating(
            UUID rideId, UUID ratedUserId, UUID raterUserId, String raterRole, short score) {
        jdbcTemplate.update(
                "INSERT INTO ratings (id, country_code, ride_id, rated_user_id, rater_user_id,"
                        + " rater_role, score, created_at, updated_at) VALUES (?, 'TZ', ?, ?, ?, ?,"
                        + " ?, now(), now())",
                UUID.randomUUID(),
                rideId,
                ratedUserId,
                raterUserId,
                raterRole,
                score);
    }
}
