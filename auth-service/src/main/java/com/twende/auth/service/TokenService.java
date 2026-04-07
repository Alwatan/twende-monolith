package com.twende.auth.service;

import com.twende.auth.config.AuthProperties;
import com.twende.auth.dto.TokenResponseDto;
import com.twende.auth.entity.AuthUser;
import com.twende.auth.entity.RevokedToken;
import com.twende.auth.repository.RevokedTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private static final String REVOKED_TOKEN_PREFIX = "token:revoked:";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final RedisTemplate<String, String> redisTemplate;
    private final RevokedTokenRepository revokedTokenRepository;
    private final AuthProperties authProperties;

    /**
     * Issue access and refresh tokens for an authenticated user.
     *
     * @param user the authenticated AuthUser
     * @param isNewUser true if this is the user's first login
     * @return TokenResponseDto containing both tokens and metadata
     */
    public TokenResponseDto issueTokens(AuthUser user, boolean isNewUser) {
        Instant now = Instant.now();
        long accessTtlSeconds = authProperties.getJwt().getAccessTokenTtlSeconds();
        int refreshTtlDays = authProperties.getJwt().getRefreshTokenTtlDays();

        String accessToken = createAccessToken(user, now, accessTtlSeconds);
        String refreshToken = createRefreshToken(user, now, refreshTtlDays);

        return TokenResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTtlSeconds)
                .isNewUser(isNewUser)
                .build();
    }

    /**
     * Revoke a refresh token by adding its JTI to the Redis blocklist and persisting to DB.
     *
     * @param token the raw refresh token string
     */
    public void revokeToken(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        String jti = jwt.getId();
        Instant expiresAt = jwt.getExpiresAt();

        if (jti == null) {
            log.warn("Token has no JTI claim, cannot revoke");
            return;
        }

        if (expiresAt == null) {
            log.warn("Token has no expiry claim, using default TTL for revocation");
            expiresAt =
                    Instant.now()
                            .plus(
                                    authProperties.getJwt().getRefreshTokenTtlDays(),
                                    ChronoUnit.DAYS);
        }

        long remainingSeconds = Duration.between(Instant.now(), expiresAt).getSeconds();
        if (remainingSeconds > 0) {
            redisTemplate
                    .opsForValue()
                    .set(REVOKED_TOKEN_PREFIX + jti, "1", remainingSeconds, TimeUnit.SECONDS);
        }

        RevokedToken revokedToken = new RevokedToken();
        revokedToken.setJti(jti);
        revokedToken.setRevokedAt(Instant.now());
        revokedToken.setExpiresAt(expiresAt);
        revokedTokenRepository.save(revokedToken);

        log.info("Revoked token with JTI: {}", jti);
    }

    /**
     * Check whether a token with the given JTI has been revoked.
     *
     * @param jti the JWT ID claim
     * @return true if the token is revoked
     */
    public boolean isRevoked(String jti) {
        Boolean inRedis = redisTemplate.hasKey(REVOKED_TOKEN_PREFIX + jti);
        if (Boolean.TRUE.equals(inRedis)) {
            return true;
        }
        return revokedTokenRepository.existsByJti(jti);
    }

    private String createAccessToken(AuthUser user, Instant issuedAt, long ttlSeconds) {
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .subject(user.getId().toString())
                        .issuedAt(issuedAt)
                        .expiresAt(issuedAt.plusSeconds(ttlSeconds))
                        .claim("roles", List.of(user.getRole().name()))
                        .claim("countryCode", user.getCountryCode())
                        .claim("phoneVerified", user.isPhoneVerified())
                        .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private String createRefreshToken(AuthUser user, Instant issuedAt, int ttlDays) {
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .id(UUID.randomUUID().toString())
                        .subject(user.getId().toString())
                        .issuedAt(issuedAt)
                        .expiresAt(issuedAt.plus(ttlDays, ChronoUnit.DAYS))
                        .claim("type", "refresh")
                        .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
