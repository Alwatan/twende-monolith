package tz.co.twende.gateway.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    private static final List<String> DEFAULT_ORIGINS =
            List.of("https://admin.twende.co.tz", "https://app.twende.co.tz");

    private static final List<String> DEV_ORIGINS =
            List.of("http://localhost:3000", "http://localhost:5173", "http://localhost:8080");

    @Value("${CORS_ALLOWED_ORIGINS:}")
    private String corsAllowedOrigins;

    @Value("${TWENDE_ENV:dev}")
    private String environment;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins;
        if (!corsAllowedOrigins.isBlank()) {
            origins = Arrays.asList(corsAllowedOrigins.split(","));
        } else if ("prod".equals(environment) || "production".equals(environment)) {
            origins = DEFAULT_ORIGINS;
        } else {
            origins = new java.util.ArrayList<>(DEFAULT_ORIGINS);
            origins.addAll(DEV_ORIGINS);
        }
        config.setAllowedOrigins(origins);

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type",
                        "Accept",
                        "X-Requested-With",
                        "Cache-Control"));
        config.setExposedHeaders(
                List.of(
                        "X-RateLimit-Remaining",
                        "X-RateLimit-Burst-Capacity",
                        "X-RateLimit-Replenish-Rate"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
