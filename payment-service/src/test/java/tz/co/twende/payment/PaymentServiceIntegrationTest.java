package tz.co.twende.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import tz.co.twende.payment.client.ConfigClient;
import tz.co.twende.payment.client.SubscriptionServiceClient;
import tz.co.twende.payment.entity.DriverWallet;
import tz.co.twende.payment.repository.CashDeclarationRepository;
import tz.co.twende.payment.repository.DriverWalletRepository;
import tz.co.twende.payment.repository.TransactionRepository;
import tz.co.twende.payment.repository.WalletEntryRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class PaymentServiceIntegrationTest {

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
    @MockitoBean private SubscriptionServiceClient subscriptionServiceClient;
    @MockitoBean private ConfigClient configClient;

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private DriverWalletRepository walletRepository;
    @Autowired private WalletEntryRepository walletEntryRepository;
    @Autowired private CashDeclarationRepository cashDeclarationRepository;

    @org.springframework.boot.test.web.server.LocalServerPort private int port;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        cashDeclarationRepository.deleteAll();
        walletEntryRepository.deleteAll();
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void givenValidRidePayment_whenPostInternalRide_thenTransactionCreated() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        webTestClient
                .post()
                .uri("/internal/payments/ride")
                .bodyValue(
                        java.util.Map.of(
                                "rideId",
                                rideId,
                                "riderId",
                                riderId,
                                "driverId",
                                driverId,
                                "amount",
                                5000.00,
                                "currencyCode",
                                "TZS",
                                "countryCode",
                                "TZ",
                                "freeRide",
                                false))
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.status")
                .isEqualTo("COMPLETED");

        assertTrue(transactionRepository.existsByReferenceIdAndReferenceType(rideId, "RIDE"));
    }

    @Test
    void givenFreeRide_whenPostInternalRide_thenDriverWalletCredited() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        webTestClient
                .post()
                .uri("/internal/payments/ride")
                .bodyValue(
                        java.util.Map.of(
                                "rideId",
                                rideId,
                                "riderId",
                                riderId,
                                "driverId",
                                driverId,
                                "amount",
                                3000.00,
                                "currencyCode",
                                "TZS",
                                "countryCode",
                                "TZ",
                                "freeRide",
                                true))
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus()
                .isOk();

        DriverWallet wallet = walletRepository.findByDriverId(driverId).orElse(null);
        assertNotNull(wallet);
        assertEquals(0, new BigDecimal("3000.00").compareTo(wallet.getBalance()));
    }

    @Test
    void givenDriverWithWallet_whenGetWallet_thenReturnsBalance() {
        UUID driverId = UUID.randomUUID();
        DriverWallet wallet = new DriverWallet(driverId, "TZ", "TZS");
        wallet.setBalance(new BigDecimal("10000.00"));
        walletRepository.save(wallet);

        webTestClient
                .get()
                .uri("/api/v1/payments/wallet")
                .header("X-User-Id", driverId.toString())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.balance")
                .isEqualTo(10000.00);
    }

    @Test
    void givenDriverWithWallet_whenCashDeclare_thenDeclarationCreated() {
        UUID driverId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();
        DriverWallet wallet = new DriverWallet(driverId, "TZ", "TZS");
        walletRepository.save(wallet);

        webTestClient
                .post()
                .uri("/api/v1/payments/" + rideId + "/cash-declare")
                .bodyValue(java.util.Map.of("amount", 4000.00))
                .header("Content-Type", "application/json")
                .header("X-User-Id", driverId.toString())
                .header("X-Country-Code", "TZ")
                .exchange()
                .expectStatus()
                .isOk();

        assertTrue(cashDeclarationRepository.existsByRideId(rideId));
        DriverWallet updated = walletRepository.findByDriverId(driverId).orElseThrow();
        assertEquals(0, new BigDecimal("4000.00").compareTo(updated.getBalance()));
    }

    @Test
    void givenDriverWithBalance_whenWithdraw_thenBalanceDeducted() {
        UUID driverId = UUID.randomUUID();
        DriverWallet wallet = new DriverWallet(driverId, "TZ", "TZS");
        wallet.setBalance(new BigDecimal("5000.00"));
        walletRepository.save(wallet);

        webTestClient
                .post()
                .uri("/api/v1/payments/withdraw")
                .bodyValue(java.util.Map.of("amount", 2000.00, "mobileNumber", "+255712345678"))
                .header("Content-Type", "application/json")
                .header("X-User-Id", driverId.toString())
                .header("X-Country-Code", "TZ")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.status")
                .isEqualTo("COMPLETED");
    }

    @Test
    void givenExistingTransaction_whenGetByRideId_thenReturnsTransaction() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        // Create via internal API first
        webTestClient
                .post()
                .uri("/internal/payments/ride")
                .bodyValue(
                        java.util.Map.of(
                                "rideId",
                                rideId,
                                "riderId",
                                UUID.randomUUID(),
                                "driverId",
                                driverId,
                                "amount",
                                2500.00,
                                "currencyCode",
                                "TZS",
                                "countryCode",
                                "TZ",
                                "freeRide",
                                false))
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus()
                .isOk();

        webTestClient
                .get()
                .uri("/api/v1/payments/" + rideId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.referenceId")
                .isEqualTo(rideId.toString());
    }

    @Test
    void givenDriverWithEarnings_whenGetEarnings_thenReturnsSummary() {
        UUID driverId = UUID.randomUUID();

        // Create wallet and record some earnings via free ride
        webTestClient
                .post()
                .uri("/internal/payments/ride")
                .bodyValue(
                        java.util.Map.of(
                                "rideId",
                                UUID.randomUUID(),
                                "riderId",
                                UUID.randomUUID(),
                                "driverId",
                                driverId,
                                "amount",
                                5000.00,
                                "currencyCode",
                                "TZS",
                                "countryCode",
                                "TZ",
                                "freeRide",
                                true))
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus()
                .isOk();

        webTestClient
                .get()
                .uri("/api/v1/payments/earnings")
                .header("X-User-Id", driverId.toString())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.todayEarnings")
                .isEqualTo(5000.00);
    }
}
