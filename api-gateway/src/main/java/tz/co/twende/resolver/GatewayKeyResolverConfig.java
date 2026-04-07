package tz.co.twende.gateway.resolver;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayKeyResolverConfig {

    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange ->
                Mono.just(
                        exchange.getRequest().getRemoteAddress() != null
                                ? exchange.getRequest()
                                        .getRemoteAddress()
                                        .getAddress()
                                        .getHostAddress()
                                : "unknown");
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange ->
                Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst("X-User-Id"))
                        .defaultIfEmpty("anonymous");
    }
}
