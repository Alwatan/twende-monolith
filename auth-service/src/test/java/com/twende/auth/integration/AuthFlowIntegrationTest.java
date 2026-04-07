package com.twende.auth.integration;

import static org.assertj.core.api.Assertions.*;

import com.twende.auth.dto.OtpRequestDto;
import com.twende.auth.dto.OtpVerifyDto;
import com.twende.auth.entity.OtpCode;
import com.twende.auth.repository.OtpCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("twende_auth_test")
                    .withUsername("twende")
                    .withPassword("twende");

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

    private WebTestClient webTestClient;

    @Autowired private OtpCodeRepository otpCodeRepository;

    @Autowired private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        otpCodeRepository.deleteAll();
        var keys = redisTemplate.keys("otp:rate:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void givenValidPhone_whenRequestOtp_thenReturns200() {
        OtpRequestDto request = new OtpRequestDto("+255712345678", "TZ");

        webTestClient
                .post()
                .uri("/api/v1/auth/otp/request")
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true);

        var otpCode =
                otpCodeRepository.findTopByPhoneNumberAndUsedFalseOrderByCreatedAtDesc(
                        "+255712345678");
        assertThat(otpCode).isPresent();
        assertThat(otpCode.get().getCode()).hasSize(6);
    }

    @Test
    void givenValidOtp_whenVerify_thenReturnsTokens() {
        // Request OTP
        webTestClient
                .post()
                .uri("/api/v1/auth/otp/request")
                .bodyValue(new OtpRequestDto("+255712000001", "TZ"))
                .exchange()
                .expectStatus()
                .isOk();

        // Get OTP from DB
        OtpCode otpCode =
                otpCodeRepository
                        .findTopByPhoneNumberAndUsedFalseOrderByCreatedAtDesc("+255712000001")
                        .orElseThrow(() -> new AssertionError("OTP not found"));

        // Verify
        webTestClient
                .post()
                .uri("/api/v1/auth/otp/verify")
                .bodyValue(new OtpVerifyDto("+255712000001", otpCode.getCode()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.accessToken")
                .isNotEmpty()
                .jsonPath("$.data.refreshToken")
                .isNotEmpty()
                .jsonPath("$.data.tokenType")
                .isEqualTo("Bearer");
    }

    @Test
    void givenWrongOtp_whenVerify_thenReturns400() {
        webTestClient
                .post()
                .uri("/api/v1/auth/otp/request")
                .bodyValue(new OtpRequestDto("+255712000002", "TZ"))
                .exchange()
                .expectStatus()
                .isOk();

        webTestClient
                .post()
                .uri("/api/v1/auth/otp/verify")
                .bodyValue(new OtpVerifyDto("+255712000002", "000000"))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(false);
    }

    @Test
    void givenRateLimitExceeded_whenRequestOtp_thenReturns429() {
        String phone = "+255712000003";
        OtpRequestDto request = new OtpRequestDto(phone, "TZ");

        for (int i = 0; i < 3; i++) {
            webTestClient
                    .post()
                    .uri("/api/v1/auth/otp/request")
                    .bodyValue(request)
                    .exchange()
                    .expectStatus()
                    .isOk();
        }

        webTestClient
                .post()
                .uri("/api/v1/auth/otp/request")
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isEqualTo(429);
    }

    @Test
    void givenNewUser_whenVerifyOtp_thenIsNewUserTrue() {
        String phone = "+255712000004";

        webTestClient
                .post()
                .uri("/api/v1/auth/otp/request")
                .bodyValue(new OtpRequestDto(phone, "TZ"))
                .exchange()
                .expectStatus()
                .isOk();

        OtpCode otpCode =
                otpCodeRepository
                        .findTopByPhoneNumberAndUsedFalseOrderByCreatedAtDesc(phone)
                        .orElseThrow(() -> new AssertionError("OTP not found"));

        webTestClient
                .post()
                .uri("/api/v1/auth/otp/verify")
                .bodyValue(new OtpVerifyDto(phone, otpCode.getCode()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.newUser")
                .isEqualTo(true);
    }
}
