package com.twende.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/**
 * OAuth2 Authorization Server configuration.
 *
 * <p>Provides RSA key pair for JWT signing, JWK source, token encoder/decoder, registered OAuth2
 * clients, and authorization server settings.
 *
 * <p>In production, the RSA key pair should be loaded from an external keystore (PKCS12, Kubernetes
 * secret, or AWS KMS) rather than generated in memory.
 */
@Configuration
@Slf4j
public class AuthServerConfig {

    @Value("${twende.auth.jwt.access-token-ttl-seconds:3600}")
    private long accessTokenTtlSeconds;

    @Value("${twende.auth.jwt.refresh-token-ttl-days:30}")
    private int refreshTokenTtlDays;

    /**
     * Generates an RSA 2048-bit key pair in memory for development. In production, load from an
     * external keystore configured via twende.auth.jwt.keystore-path.
     */
    @Bean
    public KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            log.info("Generated in-memory RSA key pair for JWT signing (dev mode)");
            return keyPair;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to generate RSA key pair", e);
        }
    }

    /** JWK source exposing the RSA public key at /oauth2/jwks for resource servers. */
    @Bean
    public JWKSource<SecurityContext> jwkSource(KeyPair rsaKeyPair) {
        RSAPublicKey publicKey = (RSAPublicKey) rsaKeyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) rsaKeyPair.getPrivate();
        RSAKey rsaKey =
                new RSAKey.Builder(publicKey)
                        .privateKey(privateKey)
                        .keyID(UUID.randomUUID().toString())
                        .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    /** JWT encoder used by TokenService for programmatic token generation. */
    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /** JWT decoder used by the resource server filter chain to validate incoming tokens. */
    @Bean
    public JwtDecoder jwtDecoder(KeyPair rsaKeyPair) {
        return NimbusJwtDecoder.withPublicKey((RSAPublicKey) rsaKeyPair.getPublic()).build();
    }

    /**
     * Registered OAuth2 clients for the authorization server.
     *
     * <p>Four clients are configured:
     *
     * <ul>
     *   <li><b>twende-rider-app</b> — Rider mobile app (client_credentials, authorization_code,
     *       refresh_token)
     *   <li><b>twende-driver-app</b> — Driver mobile app (same grants)
     *   <li><b>twende-admin</b> — Admin web portal (same grants)
     *   <li><b>twende-internal</b> — Service-to-service calls (client_credentials only)
     * </ul>
     *
     * <p>All use {noop}secret for development. In production, use proper secrets via environment
     * variables or a secrets manager.
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        TokenSettings mobileTokenSettings =
                TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofSeconds(accessTokenTtlSeconds))
                        .refreshTokenTimeToLive(Duration.ofDays(refreshTokenTtlDays))
                        .reuseRefreshTokens(false) // Enable refresh token rotation
                        .build();

        TokenSettings internalTokenSettings =
                TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofSeconds(accessTokenTtlSeconds))
                        .build();

        RegisteredClient riderApp =
                RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId("twende-rider-app")
                        .clientSecret("{noop}secret")
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                        .redirectUri("twende://callback")
                        .scope("ride:read")
                        .scope("ride:write")
                        .scope("profile:read")
                        .tokenSettings(mobileTokenSettings)
                        .build();

        RegisteredClient driverApp =
                RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId("twende-driver-app")
                        .clientSecret("{noop}secret")
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                        .redirectUri("twende-driver://callback")
                        .scope("ride:read")
                        .scope("ride:write")
                        .scope("profile:read")
                        .scope("driver:manage")
                        .tokenSettings(mobileTokenSettings)
                        .build();

        RegisteredClient adminApp =
                RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId("twende-admin")
                        .clientSecret("{noop}secret")
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                        .redirectUri("http://localhost:3000/callback")
                        .scope("admin:read")
                        .scope("admin:write")
                        .scope("profile:read")
                        .tokenSettings(mobileTokenSettings)
                        .build();

        RegisteredClient internalClient =
                RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId("twende-internal")
                        .clientSecret("{noop}secret")
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .scope("internal")
                        .tokenSettings(internalTokenSettings)
                        .build();

        return new InMemoryRegisteredClientRepository(
                riderApp, driverApp, adminApp, internalClient);
    }

    /** Authorization server settings including the issuer URI. */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings(
            @Value("${spring.security.oauth2.authorizationserver.issuer-uri:http://localhost:8081}")
                    String issuerUri) {
        return AuthorizationServerSettings.builder().issuer(issuerUri).build();
    }
}
