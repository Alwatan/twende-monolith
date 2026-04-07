package com.twende.gateway.resolver;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.InetAddress;
import java.net.InetSocketAddress;
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
class IpKeyResolverTest {

    private KeyResolver keyResolver;

    @BeforeEach
    void setUp() {
        IpKeyResolver ipKeyResolver = new IpKeyResolver();
        keyResolver = ipKeyResolver.ipKeyResolver();
        assertNotNull(keyResolver);
    }

    @Test
    void givenRequest_whenResolve_thenReturnsClientIp() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/auth/otp/request")
                        .remoteAddress(
                                new InetSocketAddress(InetAddress.getLoopbackAddress(), 12345))
                        .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(keyResolver.resolve(exchange)).expectNext("127.0.0.1").verifyComplete();
    }

    @Test
    void givenRequestWithNoRemoteAddress_whenResolve_thenReturnsUnknown() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/auth/otp/request").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(keyResolver.resolve(exchange)).expectNext("unknown").verifyComplete();
    }
}
