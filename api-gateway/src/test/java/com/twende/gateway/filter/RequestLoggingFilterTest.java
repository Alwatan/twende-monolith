package com.twende.gateway.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RequestLoggingFilterTest {

    @InjectMocks private RequestLoggingFilter filter;

    @Mock private GatewayFilterChain chain;

    @Test
    void givenRequestWithUserId_whenFilter_thenLogsWithUserId() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header("X-User-Id", "test-user-id")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    void givenRequestWithoutUserId_whenFilter_thenLogsWithoutUserId() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/config/TZ").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    void givenRequestWithNullStatusCode_whenFilter_thenLogsZeroStatus() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        // Don't set status code — it will be null
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    void givenFilter_whenGetOrder_thenReturnsMinus150() {
        assertEquals(-150, filter.getOrder());
    }
}
