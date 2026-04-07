package com.twende.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Application security configuration.
 *
 * <p>The Authorization Server filter chain (OAuth2 protocol endpoints: /oauth2/token, /oauth2/jwks,
 * etc.) is auto-configured by Spring Boot's OAuth2 Authorization Server starter. This class only
 * defines the default application security chain for our custom API endpoints.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Default application security filter chain. Configures:
     *
     * <ul>
     *   <li>CSRF disabled (stateless API)
     *   <li>Stateless session management (no server-side sessions)
     *   <li>Public access to OTP endpoints, actuator health/info
     *   <li>JWT Bearer token validation for all other endpoints
     * </ul>
     */
    @Bean
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/api/v1/auth/otp/**")
                                        .permitAll()
                                        .requestMatchers("/oauth2/**")
                                        .permitAll()
                                        .requestMatchers(
                                                "/.well-known/oauth-authorization-server",
                                                "/.well-known/openid-configuration")
                                        .permitAll()
                                        .requestMatchers("/actuator/health", "/actuator/info")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
