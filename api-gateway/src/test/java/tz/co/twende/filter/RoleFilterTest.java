package tz.co.twende.gateway.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RoleFilterTest {

    private RoleFilter roleFilter;

    @BeforeEach
    void setUp() {
        roleFilter = new RoleFilter();
    }

    @Test
    void givenMatchingRole_whenFilter_thenPasses() {
        RoleFilter.Config config = new RoleFilter.Config();
        config.setRoles(List.of("ADMIN", "DRIVER"));
        GatewayFilter filter = roleFilter.apply(config);

        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/analytics/dashboard")
                        .header("X-User-Role", "ADMIN")
                        .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    void givenNonMatchingRole_whenFilter_thenReturns403() {
        RoleFilter.Config config = new RoleFilter.Config();
        config.setRoles(List.of("ADMIN"));
        GatewayFilter filter = roleFilter.apply(config);

        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/compliance/reports")
                        .header("X-User-Role", "RIDER")
                        .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void givenMissingRoleHeader_whenFilter_thenReturns403() {
        RoleFilter.Config config = new RoleFilter.Config();
        config.setRoles(List.of("ADMIN"));
        GatewayFilter filter = roleFilter.apply(config);

        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/compliance/reports").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void givenMultipleAllowedRoles_whenDriverRole_thenPasses() {
        RoleFilter.Config config = new RoleFilter.Config();
        config.setRoles(List.of("DRIVER", "ADMIN"));
        GatewayFilter filter = roleFilter.apply(config);

        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/analytics/earnings")
                        .header("X-User-Role", "DRIVER")
                        .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
    }
}
