package com.twende.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");

        return chain.filter(exchange)
                .then(
                        Mono.fromRunnable(
                                () -> {
                                    long duration = System.currentTimeMillis() - startTime;
                                    int status =
                                            exchange.getResponse().getStatusCode() != null
                                                    ? exchange.getResponse().getStatusCode().value()
                                                    : 0;
                                    if (userId != null) {
                                        log.info(
                                                "{} {} userId={} status={} duration={}ms",
                                                method,
                                                path,
                                                userId,
                                                status,
                                                duration);
                                    } else {
                                        log.info(
                                                "{} {} status={} duration={}ms",
                                                method,
                                                path,
                                                status,
                                                duration);
                                    }
                                }));
    }

    @Override
    public int getOrder() {
        return -150;
    }
}
