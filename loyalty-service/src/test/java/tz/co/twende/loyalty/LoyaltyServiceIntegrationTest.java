package tz.co.twende.loyalty;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import tz.co.twende.common.enums.OfferStatus;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.loyalty.entity.FreeRideOffer;
import tz.co.twende.loyalty.entity.RiderProgress;
import tz.co.twende.loyalty.repository.FreeRideOfferRepository;
import tz.co.twende.loyalty.repository.LoyaltyRuleRepository;
import tz.co.twende.loyalty.repository.RiderProgressRepository;
import tz.co.twende.loyalty.service.LoyaltyService;
import tz.co.twende.loyalty.service.OfferExpiryScheduler;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class LoyaltyServiceIntegrationTest {

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

    @org.springframework.boot.test.web.server.LocalServerPort private int port;

    @Autowired private LoyaltyService loyaltyService;
    @Autowired private RiderProgressRepository progressRepository;
    @Autowired private FreeRideOfferRepository offerRepository;
    @Autowired private LoyaltyRuleRepository ruleRepository;
    @Autowired private OfferExpiryScheduler offerExpiryScheduler;
    @Autowired private JdbcTemplate jdbcTemplate;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        offerRepository.deleteAll();
        progressRepository.deleteAll();
        ruleRepository.deleteAll();
    }

    @Test
    void givenRideCompleted_whenProcessed_thenProgressCreated() {
        UUID riderId = UUID.randomUUID();
        insertRule("TZ", "BAJAJ", 20, "100.00", "5.00", 7);

        RideCompletedEvent event = createEvent(riderId, "BAJAJ", 5000, false);
        loyaltyService.onRideCompleted(event);

        List<RiderProgress> progressList = progressRepository.findByRiderId(riderId);
        assertEquals(1, progressList.size());
        assertEquals(1, progressList.get(0).getRideCount());
        assertEquals(new BigDecimal("5.00"), progressList.get(0).getTotalDistanceKm());
    }

    @Test
    void givenThresholdMet_whenRideCompleted_thenOfferCreatedAndProgressReset() {
        UUID riderId = UUID.randomUUID();
        insertRule("TZ", "BAJAJ", 2, "5.00", "5.00", 7);

        // First ride
        RideCompletedEvent event1 = createEvent(riderId, "BAJAJ", 3000, false);
        loyaltyService.onRideCompleted(event1);

        // Second ride reaches threshold
        RideCompletedEvent event2 = createEvent(riderId, "BAJAJ", 3000, false);
        loyaltyService.onRideCompleted(event2);

        // Progress should be reset
        List<RiderProgress> progressList = progressRepository.findByRiderId(riderId);
        assertEquals(1, progressList.size());
        assertEquals(0, progressList.get(0).getRideCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(progressList.get(0).getTotalDistanceKm()));

        // Offer should exist
        List<FreeRideOffer> offers =
                offerRepository.findByRiderIdAndStatus(riderId, OfferStatus.AVAILABLE.name());
        assertEquals(1, offers.size());
        assertEquals("BAJAJ", offers.get(0).getVehicleType());
    }

    @Test
    void givenFreeRide_whenProcessed_thenProgressNotIncremented() {
        UUID riderId = UUID.randomUUID();
        insertRule("TZ", "BAJAJ", 20, "100.00", "5.00", 7);

        // First paid ride
        RideCompletedEvent paidEvent = createEvent(riderId, "BAJAJ", 5000, false);
        loyaltyService.onRideCompleted(paidEvent);

        // Free ride - should not count
        RideCompletedEvent freeEvent = createEvent(riderId, "BAJAJ", 3000, true);
        loyaltyService.onRideCompleted(freeEvent);

        List<RiderProgress> progressList = progressRepository.findByRiderId(riderId);
        assertEquals(1, progressList.size());
        assertEquals(1, progressList.get(0).getRideCount());
    }

    @Test
    void givenApplicableOffer_whenQueried_thenReturnedViaApi() {
        UUID riderId = UUID.randomUUID();
        UUID offerId =
                insertOffer(
                        riderId,
                        "TZ",
                        "BAJAJ",
                        "5.00",
                        "AVAILABLE",
                        Instant.now().plus(5, ChronoUnit.DAYS));

        webTestClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/internal/loyalty/offers/applicable")
                                        .queryParam("riderId", riderId)
                                        .queryParam("countryCode", "TZ")
                                        .queryParam("vehicleType", "BAJAJ")
                                        .queryParam("distanceKm", "3.00")
                                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.vehicleType")
                .isEqualTo("BAJAJ");
    }

    @Test
    void givenAvailableOffer_whenRedeemed_thenStatusUpdated() {
        UUID riderId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();
        UUID offerId =
                insertOffer(
                        riderId,
                        "TZ",
                        "BAJAJ",
                        "5.00",
                        "AVAILABLE",
                        Instant.now().plus(5, ChronoUnit.DAYS));

        webTestClient
                .post()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/internal/loyalty/offers/{id}/redeem")
                                        .queryParam("rideId", rideId)
                                        .build(offerId))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true);

        FreeRideOffer redeemed = offerRepository.findById(offerId).orElseThrow();
        assertEquals(OfferStatus.REDEEMED.name(), redeemed.getStatus());
        assertEquals(rideId, redeemed.getRideId());
    }

    @Test
    void givenExpiredOffer_whenSchedulerRuns_thenOfferMarkedExpired() {
        UUID riderId = UUID.randomUUID();
        insertOffer(
                riderId,
                "TZ",
                "BAJAJ",
                "5.00",
                "AVAILABLE",
                Instant.now().minus(1, ChronoUnit.DAYS));

        offerExpiryScheduler.expireOffers();

        List<FreeRideOffer> available =
                offerRepository.findByRiderIdAndStatus(riderId, OfferStatus.AVAILABLE.name());
        assertTrue(available.isEmpty());
    }

    @Test
    void givenRider_whenGetProgress_thenProgressReturned() {
        UUID riderId = UUID.randomUUID();
        insertProgress(riderId, "TZ", "BAJAJ", 5, "25.00");

        webTestClient
                .get()
                .uri("/api/v1/loyalty/progress")
                .header("X-User-Id", riderId.toString())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data[0].rideCount")
                .isEqualTo(5);
    }

    @Test
    void givenRider_whenGetOffers_thenOffersReturned() {
        UUID riderId = UUID.randomUUID();
        insertOffer(
                riderId,
                "TZ",
                "BAJAJ",
                "5.00",
                "AVAILABLE",
                Instant.now().plus(5, ChronoUnit.DAYS));

        webTestClient
                .get()
                .uri("/api/v1/loyalty/offers")
                .header("X-User-Id", riderId.toString())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.length()")
                .isEqualTo(1);
    }

    private RideCompletedEvent createEvent(
            UUID riderId, String vehicleType, int distanceMetres, boolean freeRide) {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setRideId(UUID.randomUUID());
        event.setRiderId(riderId);
        event.setDriverId(UUID.randomUUID());
        event.setVehicleType(vehicleType);
        event.setDistanceMetres(distanceMetres);
        event.setFreeRide(freeRide);
        event.setCountryCode("TZ");
        event.setFinalFare(new BigDecimal("5000"));
        return event;
    }

    private void insertRule(
            String countryCode,
            String vehicleType,
            int requiredRides,
            String requiredDistanceKm,
            String freeRideMaxDistanceKm,
            int offerValidityDays) {
        // Check if rule already exists
        if (ruleRepository
                .findByCountryCodeAndVehicleTypeAndIsActiveTrue(countryCode, vehicleType)
                .isEmpty()) {
            jdbcTemplate.update(
                    "INSERT INTO loyalty_rules (id, country_code, vehicle_type, required_rides,"
                            + " required_distance_km, free_ride_max_distance_km, offer_validity_days,"
                            + " is_active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, true,"
                            + " now(), now())",
                    UUID.randomUUID(),
                    countryCode,
                    vehicleType,
                    requiredRides,
                    new BigDecimal(requiredDistanceKm),
                    new BigDecimal(freeRideMaxDistanceKm),
                    offerValidityDays);
        }
    }

    private UUID insertOffer(
            UUID riderId,
            String countryCode,
            String vehicleType,
            String maxDistanceKm,
            String status,
            Instant expiresAt) {
        UUID offerId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO free_ride_offers (id, rider_id, country_code, vehicle_type,"
                        + " max_distance_km, status, earned_at, expires_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, now(), ?, now(), now())",
                offerId,
                riderId,
                countryCode,
                vehicleType,
                new BigDecimal(maxDistanceKm),
                status,
                Timestamp.from(expiresAt));
        return offerId;
    }

    private void insertProgress(
            UUID riderId,
            String countryCode,
            String vehicleType,
            int rideCount,
            String totalDistanceKm) {
        jdbcTemplate.update(
                "INSERT INTO rider_progress (id, rider_id, country_code, vehicle_type, ride_count,"
                        + " total_distance_km, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, now(),"
                        + " now())",
                UUID.randomUUID(),
                riderId,
                countryCode,
                vehicleType,
                rideCount,
                new BigDecimal(totalDistanceKm));
    }
}
