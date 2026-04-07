package com.twende.gateway.resolver;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class UserKeyResolverTest {

    private KeyResolver keyResolver;

    @BeforeEach
    void setUp() {
        UserKeyResolver userKeyResolver = new UserKeyResolver();
        keyResolver = userKeyResolver.userKeyResolver();
        assertNotNull(keyResolver);
    }

    @Test
    void givenUserIdHeader_whenResolve_thenReturnsUserId() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/rides")
                        .header("X-User-Id", "user-uuid-abc-123")
                        .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(keyResolver.resolve(exchange))
                .expectNext("user-uuid-abc-123")
                .verifyComplete();
    }

    @Test
    void givenNoUserIdHeader_whenResolve_thenReturnsAnonymous() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/auth/otp/request").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(keyResolver.resolve(exchange)).expectNext("anonymous").verifyComplete();
    }
}
