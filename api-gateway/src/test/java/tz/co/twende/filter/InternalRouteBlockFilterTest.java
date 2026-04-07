package tz.co.twende.gateway.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class InternalRouteBlockFilterTest {

    private InternalRouteBlockFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalRouteBlockFilter();
    }

    @Test
    void givenInternalPath_whenFilter_thenReturns404() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/internal/service-health").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
    }

    @Test
    void givenNonInternalPath_whenFilter_thenPasses() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void givenInternalNestedPath_whenFilter_thenReturns404() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/internal/events/publish").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
    }

    @Test
    void givenOrderCalled_whenGetOrder_thenReturnsMinus200() {
        assertEquals(-200, filter.getOrder());
    }
}
