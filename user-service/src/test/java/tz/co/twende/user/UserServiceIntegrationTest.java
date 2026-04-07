package tz.co.twende.user;

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
import tz.co.twende.user.entity.SavedPlace;
import tz.co.twende.user.entity.UserProfile;
import tz.co.twende.user.repository.SavedPlaceRepository;
import tz.co.twende.user.repository.UserProfileRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class UserServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("twende_users_test")
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

    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private SavedPlaceRepository savedPlaceRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        savedPlaceRepository.deleteAll();
        userProfileRepository.deleteAll();
    }

    @Test
    void givenExistingUser_whenGetProfile_thenReturn200() {
        UUID userId = UUID.randomUUID();
        createAndSaveProfile(userId, "Jane Doe", "TZ");

        webTestClient
                .get()
                .uri("/api/v1/users/me")
                .header("X-User-Id", userId.toString())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.fullName")
                .isEqualTo("Jane Doe");
    }

    @Test
    void givenNonExistingUser_whenGetProfile_thenReturn404() {
        UUID userId = UUID.randomUUID();

        webTestClient
                .get()
                .uri("/api/v1/users/me")
                .header("X-User-Id", userId.toString())
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void givenExistingUser_whenUpdateProfile_thenReturnUpdated() {
        UUID userId = UUID.randomUUID();
        createAndSaveProfile(userId, "Jane Doe", "TZ");

        webTestClient
                .put()
                .uri("/api/v1/users/me")
                .header("X-User-Id", userId.toString())
                .bodyValue(Map.of("fullName", "Jane Updated", "email", "jane@test.com"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.fullName")
                .isEqualTo("Jane Updated")
                .jsonPath("$.data.email")
                .isEqualTo("jane@test.com");

        UserProfile updated = userProfileRepository.findById(userId).orElseThrow();
        assertThat(updated.getFullName()).isEqualTo("Jane Updated");
    }

    @Test
    void givenUser_whenCreateSavedPlace_thenReturn201() {
        UUID userId = UUID.randomUUID();
        createAndSaveProfile(userId, "Jane Doe", "TZ");

        webTestClient
                .post()
                .uri("/api/v1/users/me/saved-places")
                .header("X-User-Id", userId.toString())
                .header("X-Country-Code", "TZ")
                .bodyValue(
                        Map.of(
                                "label",
                                "Home",
                                "address",
                                "Mikocheni, Dar es Salaam",
                                "latitude",
                                -6.79,
                                "longitude",
                                39.21))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.data.label")
                .isEqualTo("Home");

        assertThat(savedPlaceRepository.findByUserId(userId)).hasSize(1);
    }

    @Test
    void givenSavedPlaces_whenListPlaces_thenReturnAll() {
        UUID userId = UUID.randomUUID();
        createAndSaveProfile(userId, "Jane Doe", "TZ");
        createAndSavePlace(userId, "Home", -6.79, 39.21);
        createAndSavePlace(userId, "Work", -6.81, 39.27);

        webTestClient
                .get()
                .uri("/api/v1/users/me/saved-places")
                .header("X-User-Id", userId.toString())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.length()")
                .isEqualTo(2);
    }

    @Test
    void givenSavedPlace_whenDeleteByOwner_thenReturn200() {
        UUID userId = UUID.randomUUID();
        createAndSaveProfile(userId, "Jane Doe", "TZ");
        SavedPlace place = createAndSavePlace(userId, "Home", -6.79, 39.21);

        webTestClient
                .delete()
                .uri("/api/v1/users/me/saved-places/{id}", place.getId())
                .header("X-User-Id", userId.toString())
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(savedPlaceRepository.findByUserId(userId)).isEmpty();
    }

    private void createAndSaveProfile(UUID userId, String fullName, String countryCode) {
        jdbcTemplate.update(
                "INSERT INTO users (id, full_name, country_code, is_active, created_at, updated_at) "
                        + "VALUES (?, ?, ?, true, now(), now())",
                userId,
                fullName,
                countryCode);
    }

    private SavedPlace createAndSavePlace(UUID userId, String label, double lat, double lng) {
        SavedPlace place = new SavedPlace();
        place.setUserId(userId);
        place.setCountryCode("TZ");
        place.setLabel(label);
        place.setAddress("Test Address, Dar es Salaam");
        place.setLatitude(lat);
        place.setLongitude(lng);
        return savedPlaceRepository.save(place);
    }
}
