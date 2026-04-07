package tz.co.twende.gateway.resolver;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayKeyResolverConfig {

    /**
     * Rate limit key based on client IP address. Supports X-Forwarded-For header for production
     * deployments behind a load balancer or reverse proxy. Falls back to the direct remote address
     * if no forwarding header is present.
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // X-Forwarded-For can contain multiple IPs: "client, proxy1, proxy2"
                // The first one is the original client IP
                String clientIp = forwarded.split(",")[0].trim();
                return Mono.just(clientIp);
            }
            return Mono.just(
                    exchange.getRequest().getRemoteAddress() != null
                            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                            : "unknown");
        };
    }

    /**
     * Rate limit key based on authenticated user ID. Uses the X-User-Id header injected by
     * AuthFilter after JWT validation. Falls back to "anonymous" for unauthenticated requests.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange ->
                Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst("X-User-Id"))
                        .defaultIfEmpty("anonymous");
    }
}
