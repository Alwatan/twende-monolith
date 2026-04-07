package tz.co.twende.gateway.integration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {

    @LocalServerPort private int port;

    private WebTestClient webTestClient;

    @MockitoBean private ReactiveJwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        when(jwtDecoder.decode(anyString()))
                .thenReturn(Mono.error(new JwtException("Invalid token")));
    }

    @Test
    void givenInternalPath_whenRequest_thenReturns404() {
        webTestClient.get().uri("/internal/anything").exchange().expectStatus().isNotFound();
    }

    @Test
    void givenActuatorHealth_whenRequest_thenReturns200() {
        webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void givenNoToken_whenAccessProtectedRoute_thenReturns401() {
        webTestClient.get().uri("/api/v1/users/me").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void givenInvalidToken_whenAccessProtectedRoute_thenReturns401() {
        webTestClient
                .get()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    // Note: Valid JWT pass-through test is covered by AuthFilterTest (unit test).
    // @MockitoBean does not reliably replace the ReactiveJwtDecoder in the
    // GatewayFilterFactory context during @SpringBootTest with Gateway 5.0.
}
