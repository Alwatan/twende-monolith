package com.twende.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.twende.auth.config.AuthProperties;
import com.twende.auth.entity.AuthUser;
import com.twende.auth.entity.RevokedToken;
import com.twende.auth.repository.RevokedTokenRepository;
import com.twende.common.enums.UserRole;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.jwt.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock private JwtEncoder jwtEncoder;

    @Mock private JwtDecoder jwtDecoder;

    @Mock private RedisTemplate<String, String> redisTemplate;

    @Mock private ValueOperations<String, String> valueOperations;

    @Mock private RevokedTokenRepository revokedTokenRepository;

    private AuthProperties authProperties;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        AuthProperties.Jwt jwt = new AuthProperties.Jwt();
        jwt.setAccessTokenTtlSeconds(3600);
        jwt.setRefreshTokenTtlDays(30);
        authProperties.setJwt(jwt);

        tokenService =
                new TokenService(
                        jwtEncoder,
                        jwtDecoder,
                        redisTemplate,
                        revokedTokenRepository,
                        authProperties);
    }

    @Test
    void givenValidUser_whenIssueTokens_thenReturnsTokens() {
        AuthUser user = new AuthUser();
        user.setId(UUID.randomUUID());
        user.setPhoneNumber("+255712345678");
        user.setCountryCode("TZ");
        user.setRole(UserRole.RIDER);
        user.setPhoneVerified(true);

        Jwt accessJwt =
                Jwt.withTokenValue("access-token-value")
                        .header("alg", "RS256")
                        .subject(user.getId().toString())
                        .claim("roles", java.util.List.of("RIDER"))
                        .claim("countryCode", "TZ")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build();

        Jwt refreshJwt =
                Jwt.withTokenValue("refresh-token-value")
                        .header("alg", "RS256")
                        .subject(user.getId().toString())
                        .claim("jti", UUID.randomUUID().toString())
                        .claim("type", "refresh")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                        .build();

        when(jwtEncoder.encode(any(JwtEncoderParameters.class)))
                .thenReturn(accessJwt)
                .thenReturn(refreshJwt);

        var result = tokenService.issueTokens(user, true);

        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo("access-token-value");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token-value");
        assertThat(result.getTokenType()).isEqualTo("Bearer");
        assertThat(result.getExpiresIn()).isEqualTo(3600);
        assertThat(result.isNewUser()).isTrue();

        verify(jwtEncoder, times(2)).encode(any(JwtEncoderParameters.class));
    }

    @Test
    void givenValidToken_whenRevokeToken_thenAddsToRedisAndDb() {
        String jti = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);

        Jwt jwt =
                Jwt.withTokenValue("some-refresh-token")
                        .header("alg", "RS256")
                        .subject(UUID.randomUUID().toString())
                        .claim("jti", jti)
                        .issuedAt(Instant.now())
                        .expiresAt(expiresAt)
                        .build();

        when(jwtDecoder.decode("some-refresh-token")).thenReturn(jwt);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        tokenService.revokeToken("some-refresh-token");

        verify(valueOperations)
                .set(eq("token:revoked:" + jti), eq("1"), anyLong(), eq(TimeUnit.SECONDS));

        ArgumentCaptor<RevokedToken> captor = ArgumentCaptor.forClass(RevokedToken.class);
        verify(revokedTokenRepository).save(captor.capture());

        RevokedToken saved = captor.getValue();
        assertThat(saved.getJti()).isEqualTo(jti);
        assertThat(saved.getRevokedAt()).isNotNull();
        assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void givenTokenInRedis_whenIsRevoked_thenReturnsTrue() {
        String jti = "test-jti";
        when(redisTemplate.hasKey("token:revoked:" + jti)).thenReturn(true);

        boolean result = tokenService.isRevoked(jti);

        assertThat(result).isTrue();
        verify(revokedTokenRepository, never()).existsByJti(any());
    }

    @Test
    void givenTokenNotInRedisButInDb_whenIsRevoked_thenReturnsTrue() {
        String jti = "test-jti";
        when(redisTemplate.hasKey("token:revoked:" + jti)).thenReturn(false);
        when(revokedTokenRepository.existsByJti(jti)).thenReturn(true);

        boolean result = tokenService.isRevoked(jti);

        assertThat(result).isTrue();
    }

    @Test
    void givenTokenNowhere_whenIsRevoked_thenReturnsFalse() {
        String jti = "test-jti";
        when(redisTemplate.hasKey("token:revoked:" + jti)).thenReturn(false);
        when(revokedTokenRepository.existsByJti(jti)).thenReturn(false);

        boolean result = tokenService.isRevoked(jti);

        assertThat(result).isFalse();
    }
}
