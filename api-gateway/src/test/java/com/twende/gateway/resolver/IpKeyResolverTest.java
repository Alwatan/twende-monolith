package com.twende.gateway.resolver;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

class IpKeyResolverTest {

    private final KeyResolver keyResolver = new GatewayKeyResolverConfig().ipKeyResolver();

    @Test
    void givenRequest_whenResolve_thenReturnsClientIp() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/test")
                        .remoteAddress(
                                new InetSocketAddress(InetAddress.getLoopbackAddress(), 12345))
                        .build();

        StepVerifier.create(keyResolver.resolve(MockServerWebExchange.from(request)))
                .expectNext("127.0.0.1")
                .verifyComplete();
    }

    @Test
    void givenRequestWithNoRemoteAddress_whenResolve_thenReturnsUnknown() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();

        StepVerifier.create(keyResolver.resolve(MockServerWebExchange.from(request)))
                .expectNext("unknown")
                .verifyComplete();
    }
}
