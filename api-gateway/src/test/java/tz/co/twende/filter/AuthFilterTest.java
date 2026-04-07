package tz.co.twende.gateway.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    @Mock private ReactiveJwtDecoder jwtDecoder;

    private GatewayFilter filter;

    @BeforeEach
    void setUp() {
        AuthFilter authFilter = new AuthFilter(jwtDecoder);
        filter = authFilter.apply(new AuthFilter.Config());
    }

    @Test
    void givenValidJwt_whenFilter_thenInjectsHeaders() {
        Jwt jwt =
                Jwt.withTokenValue("valid-token")
                        .header("alg", "RS256")
                        .subject("user-uuid-123")
                        .claim("roles", List.of("RIDER"))
                        .claim("countryCode", "TZ")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build();
        when(jwtDecoder.decode("valid-token")).thenReturn(Mono.just(jwt));

        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerWebExchange mutated = captor.getValue();

        assertEquals("user-uuid-123", mutated.getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("RIDER", mutated.getRequest().getHeaders().getFirst("X-User-Role"));
        assertEquals("TZ", mutated.getRequest().getHeaders().getFirst("X-Country-Code"));
    }

    @Test
    void givenMissingAuthHeader_whenFilter_thenReturns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void givenInvalidToken_whenFilter_thenReturns401() {
        when(jwtDecoder.decode("bad-token"))
                .thenReturn(Mono.error(new RuntimeException("Invalid token")));

        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
                        .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void givenTokenWithoutBearerPrefix_whenFilter_thenReturns401() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Basic some-token")
                        .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void givenValidJwtWithNullCountryCode_whenFilter_thenInjectsEmptyCountryCodeHeader() {
        Jwt jwt =
                Jwt.withTokenValue("valid-token")
                        .header("alg", "RS256")
                        .subject("user-uuid-456")
                        .claim("roles", List.of("DRIVER"))
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build();
        when(jwtDecoder.decode("valid-token")).thenReturn(Mono.just(jwt));

        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/drivers/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerWebExchange mutated = captor.getValue();

        assertEquals("user-uuid-456", mutated.getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("DRIVER", mutated.getRequest().getHeaders().getFirst("X-User-Role"));
        assertEquals("", mutated.getRequest().getHeaders().getFirst("X-Country-Code"));
    }

    @Test
    void givenValidJwtWithEmptyRoles_whenFilter_thenInjectsEmptyRoleHeader() {
        Jwt jwt =
                Jwt.withTokenValue("valid-token")
                        .header("alg", "RS256")
                        .subject("user-uuid-789")
                        .claim("roles", List.of())
                        .claim("countryCode", "KE")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build();
        when(jwtDecoder.decode("valid-token")).thenReturn(Mono.just(jwt));

        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerWebExchange mutated = captor.getValue();

        assertEquals("", mutated.getRequest().getHeaders().getFirst("X-User-Role"));
    }
}
