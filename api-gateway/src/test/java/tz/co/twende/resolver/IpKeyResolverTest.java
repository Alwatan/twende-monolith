package tz.co.twende.gateway.resolver;

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
    void givenDirectConnection_whenResolve_thenReturnsRemoteAddress() {
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
    void givenXForwardedFor_whenResolve_thenReturnsClientIp() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/test")
                        .header("X-Forwarded-For", "203.0.113.50")
                        .build();

        StepVerifier.create(keyResolver.resolve(MockServerWebExchange.from(request)))
                .expectNext("203.0.113.50")
                .verifyComplete();
    }

    @Test
    void givenMultipleXForwardedFor_whenResolve_thenReturnsFirstClientIp() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/test")
                        .header("X-Forwarded-For", "203.0.113.50, 70.41.3.18, 150.172.238.178")
                        .build();

        StepVerifier.create(keyResolver.resolve(MockServerWebExchange.from(request)))
                .expectNext("203.0.113.50")
                .verifyComplete();
    }

    @Test
    void givenNoRemoteAddressAndNoForwarded_whenResolve_thenReturnsUnknown() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();

        StepVerifier.create(keyResolver.resolve(MockServerWebExchange.from(request)))
                .expectNext("unknown")
                .verifyComplete();
    }
}
