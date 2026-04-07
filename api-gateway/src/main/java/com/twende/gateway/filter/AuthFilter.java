package com.twende.gateway.filter;

import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

@Component
public class AuthFilter extends AbstractGatewayFilterFactory<AuthFilter.Config> {

    private final ReactiveJwtDecoder jwtDecoder;

    public AuthFilter(ReactiveJwtDecoder jwtDecoder) {
        super(Config.class);
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader =
                    exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            String token = authHeader.substring(7);
            return jwtDecoder
                    .decode(token)
                    .flatMap(
                            jwt -> {
                                String userId = jwt.getSubject();
                                List<String> roles = jwt.getClaimAsStringList("roles");
                                String role =
                                        (roles != null && !roles.isEmpty()) ? roles.get(0) : "";
                                String countryCode = jwt.getClaimAsString("countryCode");

                                ServerWebExchange mutated =
                                        exchange.mutate()
                                                .request(
                                                        r ->
                                                                r.header("X-User-Id", userId)
                                                                        .header("X-User-Role", role)
                                                                        .header(
                                                                                "X-Country-Code",
                                                                                countryCode != null
                                                                                        ? countryCode
                                                                                        : ""))
                                                .build();
                                return chain.filter(mutated);
                            })
                    .onErrorResume(
                            e -> {
                                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                                return exchange.getResponse().setComplete();
                            });
        };
    }

    public static class Config {}
}
