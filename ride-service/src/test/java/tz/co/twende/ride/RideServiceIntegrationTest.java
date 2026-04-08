package tz.co.twende.ride;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
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
import tz.co.twende.ride.client.ConfigClient;
import tz.co.twende.ride.client.LocationClient;
import tz.co.twende.ride.client.LoyaltyClient;
import tz.co.twende.ride.client.PricingClient;
import tz.co.twende.ride.dto.response.EstimateDto;
import tz.co.twende.ride.repository.RideDriverRejectionRepository;
import tz.co.twende.ride.repository.RideRepository;
import tz.co.twende.ride.repository.RideStatusEventRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class RideServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("twende_rides_test")
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

    @Autowired private RideRepository rideRepository;
    @Autowired private RideStatusEventRepository statusEventRepository;
    @Autowired private RideDriverRejectionRepository rejectionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private KafkaTemplate<String, Object> kafkaTemplate;
    @MockitoBean private PricingClient pricingClient;
    @MockitoBean private LocationClient locationClient;
    @MockitoBean private LoyaltyClient loyaltyClient;
    @MockitoBean private ConfigClient configClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        rejectionRepository.deleteAll();
        statusEventRepository.deleteAll();
        rideRepository.deleteAll();

        // Default mocks
        when(locationClient.isInRestrictedZone(any(), any())).thenReturn(false);
        EstimateDto estimate = new EstimateDto();
        estimate.setEstimatedFare(BigDecimal.valueOf(3500));
        estimate.setCurrency("TZS");
        estimate.setDistanceMetres(5000);
        estimate.setDurationSeconds(600);
        when(pricingClient.getFareEstimate(any(), any(), any(), any(), any(), any()))
                .thenReturn(estimate);
        when(loyaltyClient.findApplicableOffer(any(), any(), any())).thenReturn(null);
    }

    @Test
    void givenValidRequest_whenCreateRide_thenReturn201() {
        UUID riderId = UUID.randomUUID();

        webTestClient
                .post()
                .uri("/api/v1/rides")
                .header("X-User-Id", riderId.toString())
                .header("X-User-Role", "RIDER")
                .header("X-Country-Code", "TZ")
                .bodyValue(
                        Map.of(
                                "vehicleType", "BAJAJ",
                                "pickupLat", -6.7728,
                                "pickupLng", 39.2310,
                                "pickupAddress", "Kariakoo Market",
                                "dropoffLat", -6.8160,
                                "dropoffLng", 39.2803,
                                "dropoffAddress", "Mlimani City"))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.status")
                .isEqualTo("REQUESTED")
                .jsonPath("$.data.vehicleType")
                .isEqualTo("BAJAJ")
                .jsonPath("$.data.estimatedFare")
                .isEqualTo(3500);
    }

    @Test
    void givenExistingRide_whenGetRide_thenReturn200() {
        UUID rideId = createTestRide(UUID.randomUUID(), "TZ", "REQUESTED");

        webTestClient
                .get()
                .uri("/api/v1/rides/{id}", rideId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.status")
                .isEqualTo("REQUESTED");
    }

    @Test
    void givenRequestedRide_whenBoostFare_thenReturn200WithUpdatedFare() {
        UUID riderId = UUID.randomUUID();
        UUID rideId = createTestRide(riderId, "TZ", "REQUESTED");
        when(configClient.getMaxFareCap("TZ", "BAJAJ")).thenReturn(BigDecimal.valueOf(50000));

        webTestClient
                .put()
                .uri("/api/v1/rides/{id}/boost", rideId)
                .header("X-User-Id", riderId.toString())
                .header("X-User-Role", "RIDER")
                .bodyValue(Map.of("boostAmount", 1000))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.estimatedFare")
                .isEqualTo(4500)
                .jsonPath("$.data.fareBoostAmount")
                .isEqualTo(1000);
    }

    @Test
    void givenRequestedRide_whenCancelRide_thenReturn200() {
        UUID riderId = UUID.randomUUID();
        UUID rideId = createTestRide(riderId, "TZ", "REQUESTED");

        webTestClient
                .delete()
                .uri("/api/v1/rides/{id}", rideId)
                .header("X-User-Id", riderId.toString())
                .header("X-User-Role", "RIDER")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.status")
                .isEqualTo("CANCELLED")
                .jsonPath("$.data.cancelledBy")
                .isEqualTo("RIDER");
    }

    @Test
    void givenInternalEndpoint_whenOfferAccepted_thenDriverAssigned() {
        UUID riderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID rideId = createTestRide(riderId, "TZ", "REQUESTED");

        webTestClient
                .put()
                .uri("/internal/rides/{rideId}/offer-accepted", rideId)
                .bodyValue(Map.of("driverId", driverId.toString(), "estimatedArrivalSeconds", 120))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.status")
                .isEqualTo("DRIVER_ASSIGNED")
                .jsonPath("$.data.driverId")
                .isEqualTo(driverId.toString());
    }

    @Test
    void givenInternalEndpoint_whenDriverRejected_thenCountIncremented() {
        UUID riderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID rideId = createTestRide(riderId, "TZ", "REQUESTED");

        webTestClient
                .put()
                .uri("/internal/rides/{rideId}/driver-rejected", rideId)
                .bodyValue(Map.of("driverId", driverId.toString()))
                .exchange()
                .expectStatus()
                .isOk();

        // Verify rejection count incremented
        webTestClient
                .get()
                .uri("/api/v1/rides/{id}", rideId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.driverRejectionCount")
                .isEqualTo(1);
    }

    @Test
    void givenActiveRides_whenGetMeActive_thenReturnRides() {
        UUID riderId = UUID.randomUUID();
        createTestRide(riderId, "TZ", "REQUESTED");

        webTestClient
                .get()
                .uri("/api/v1/rides/me/active")
                .header("X-User-Id", riderId.toString())
                .header("X-User-Role", "RIDER")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.length()")
                .isEqualTo(1);
    }

    @Test
    void givenRideHistory_whenGetMeHistory_thenReturnPaginated() {
        UUID riderId = UUID.randomUUID();
        createTestRide(riderId, "TZ", "COMPLETED");

        webTestClient
                .get()
                .uri("/api/v1/rides/me/history?page=0&size=10")
                .header("X-User-Id", riderId.toString())
                .header("X-User-Role", "RIDER")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true);
    }

    private UUID createTestRide(UUID riderId, String countryCode, String status) {
        UUID rideId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO rides (id, country_code, rider_id, vehicle_type, status,"
                        + " pickup_lat, pickup_lng, pickup_address, dropoff_lat, dropoff_lng,"
                        + " dropoff_address, estimated_fare, fare_boost_amount, currency_code,"
                        + " free_ride, driver_rejection_count, trip_start_otp_attempts,"
                        + " requested_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(),"
                        + " now(), now())",
                rideId,
                countryCode,
                riderId,
                "BAJAJ",
                status,
                BigDecimal.valueOf(-6.7728),
                BigDecimal.valueOf(39.2310),
                "Kariakoo Market",
                BigDecimal.valueOf(-6.8160),
                BigDecimal.valueOf(39.2803),
                "Mlimani City",
                BigDecimal.valueOf(3500),
                BigDecimal.ZERO,
                "TZS",
                false,
                0,
                0);
        return rideId;
    }
}
