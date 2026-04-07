package com.twende.gateway.resolver;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

class UserKeyResolverTest {

    private final KeyResolver keyResolver = new GatewayKeyResolverConfig().userKeyResolver();

    @Test
    void givenUserIdHeader_whenResolve_thenReturnsUserId() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/test").header("X-User-Id", "user-123").build();

        StepVerifier.create(keyResolver.resolve(MockServerWebExchange.from(request)))
                .expectNext("user-123")
                .verifyComplete();
    }

    @Test
    void givenNoUserIdHeader_whenResolve_thenReturnsAnonymous() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();

        StepVerifier.create(keyResolver.resolve(MockServerWebExchange.from(request)))
                .expectNext("anonymous")
                .verifyComplete();
    }
}
