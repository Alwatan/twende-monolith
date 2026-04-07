package tz.co.twende.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import tz.co.twende.auth.config.AuthProperties;
import tz.co.twende.auth.dto.LinkPhoneRequestDto;
import tz.co.twende.auth.dto.LinkSocialRequestDto;
import tz.co.twende.auth.dto.SocialLoginRequestDto;
import tz.co.twende.auth.dto.TokenResponseDto;
import tz.co.twende.auth.entity.AuthUser;
import tz.co.twende.auth.entity.AuthUserLink;
import tz.co.twende.auth.entity.OtpCode;
import tz.co.twende.auth.repository.AuthUserLinkRepository;
import tz.co.twende.auth.repository.AuthUserRepository;
import tz.co.twende.common.enums.UserRole;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ConflictException;
import tz.co.twende.common.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class SocialLoginServiceTest {

    @Mock private AuthUserRepository authUserRepository;
    @Mock private AuthUserLinkRepository authUserLinkRepository;
    @Mock private TokenService tokenService;
    @Mock private OtpService otpService;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    private AuthProperties authProperties;
    private SocialLoginService socialLoginService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String GOOGLE_SUBJECT = "google-user-123";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_NAME = "Test User";
    private static final String TEST_PHOTO = "https://photo.example.com/pic.jpg";

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        authProperties.setGoogle(new AuthProperties.Google());
        authProperties.getGoogle().setClientId("test-google-client-id");
        authProperties.setApple(new AuthProperties.Apple());
        authProperties.getApple().setClientId("test-apple-client-id");

        socialLoginService =
                new TestSocialLoginService(
                        authUserRepository,
                        authUserLinkRepository,
                        tokenService,
                        otpService,
                        kafkaTemplate,
                        authProperties);
    }

    @Test
    void givenNewGoogleUser_whenSocialLogin_thenCreatesUserAndReturnsTokens() {
        SocialLoginRequestDto request =
                SocialLoginRequestDto.builder()
                        .provider("GOOGLE")
                        .idToken("valid-token")
                        .countryCode("TZ")
                        .build();

        when(authUserLinkRepository.findByProviderAndProviderUserId("GOOGLE", GOOGLE_SUBJECT))
                .thenReturn(Optional.empty());
        when(authUserRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());
        when(authUserRepository.save(any(AuthUser.class)))
                .thenAnswer(
                        invocation -> {
                            AuthUser user = invocation.getArgument(0);
                            user.setId(USER_ID);
                            return user;
                        });

        TokenResponseDto expectedTokens =
                TokenResponseDto.builder()
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .tokenType("Bearer")
                        .expiresIn(3600)
                        .isNewUser(true)
                        .build();
        when(tokenService.issueTokens(any(AuthUser.class), eq(true))).thenReturn(expectedTokens);

        TokenResponseDto result = socialLoginService.socialLogin(request);

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.isNewUser()).isTrue();
        assertThat(result.isPhoneRequired()).isTrue();

        // Verify user was created with correct fields
        ArgumentCaptor<AuthUser> userCaptor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserRepository).save(userCaptor.capture());
        AuthUser savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(savedUser.getFullName()).isEqualTo(TEST_NAME);
        assertThat(savedUser.getProfilePhotoUrl()).isEqualTo(TEST_PHOTO);
        assertThat(savedUser.getAuthProvider()).isEqualTo("GOOGLE");
        assertThat(savedUser.getCountryCode()).isEqualTo("TZ");
        assertThat(savedUser.getRole()).isEqualTo(UserRole.RIDER);

        // Verify auth link was created
        verify(authUserLinkRepository).save(any(AuthUserLink.class));

        // Verify Kafka event was published
        verify(kafkaTemplate).send(eq("twende.users.registered"), anyString(), any());
    }

    @Test
    void givenExistingLinkedUser_whenSocialLogin_thenReturnsTokensWithoutCreating() {
        SocialLoginRequestDto request =
                SocialLoginRequestDto.builder().provider("GOOGLE").idToken("valid-token").build();

        AuthUserLink existingLink = new AuthUserLink();
        existingLink.setUserId(USER_ID);
        existingLink.setProvider("GOOGLE");
        existingLink.setProviderUserId(GOOGLE_SUBJECT);
        when(authUserLinkRepository.findByProviderAndProviderUserId("GOOGLE", GOOGLE_SUBJECT))
                .thenReturn(Optional.of(existingLink));

        AuthUser existingUser = createExistingUser();
        existingUser.setPhoneNumber("+255712345678");
        when(authUserRepository.findById(USER_ID)).thenReturn(Optional.of(existingUser));

        TokenResponseDto expectedTokens =
                TokenResponseDto.builder()
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .tokenType("Bearer")
                        .expiresIn(3600)
                        .isNewUser(false)
                        .build();
        when(tokenService.issueTokens(existingUser, false)).thenReturn(expectedTokens);

        TokenResponseDto result = socialLoginService.socialLogin(request);

        assertThat(result.isNewUser()).isFalse();
        assertThat(result.isPhoneRequired()).isFalse();
        verify(authUserRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void givenExistingUserByEmail_whenSocialLogin_thenAutoLinksAndReturnsTokens() {
        SocialLoginRequestDto request =
                SocialLoginRequestDto.builder().provider("GOOGLE").idToken("valid-token").build();

        when(authUserLinkRepository.findByProviderAndProviderUserId("GOOGLE", GOOGLE_SUBJECT))
                .thenReturn(Optional.empty());

        AuthUser existingUser = createExistingUser();
        existingUser.setPhoneNumber("+255712345678");
        when(authUserRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(existingUser));

        TokenResponseDto expectedTokens =
                TokenResponseDto.builder()
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .tokenType("Bearer")
                        .expiresIn(3600)
                        .isNewUser(false)
                        .build();
        when(tokenService.issueTokens(existingUser, false)).thenReturn(expectedTokens);

        TokenResponseDto result = socialLoginService.socialLogin(request);

        assertThat(result.isNewUser()).isFalse();
        assertThat(result.isPhoneRequired()).isFalse();

        // Verify auth link was created for existing user
        verify(authUserLinkRepository).save(any(AuthUserLink.class));
        // Verify NO new user event was published
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void givenSocialUserWithoutPhone_whenSocialLogin_thenPhoneRequiredTrue() {
        SocialLoginRequestDto request =
                SocialLoginRequestDto.builder().provider("GOOGLE").idToken("valid-token").build();

        AuthUserLink existingLink = new AuthUserLink();
        existingLink.setUserId(USER_ID);
        when(authUserLinkRepository.findByProviderAndProviderUserId("GOOGLE", GOOGLE_SUBJECT))
                .thenReturn(Optional.of(existingLink));

        AuthUser userWithoutPhone = createExistingUser();
        userWithoutPhone.setPhoneNumber(null);
        when(authUserRepository.findById(USER_ID)).thenReturn(Optional.of(userWithoutPhone));

        TokenResponseDto expectedTokens = TokenResponseDto.builder().isNewUser(false).build();
        when(tokenService.issueTokens(userWithoutPhone, false)).thenReturn(expectedTokens);

        TokenResponseDto result = socialLoginService.socialLogin(request);
        assertThat(result.isPhoneRequired()).isTrue();
    }

    @Test
    void givenNoCountryCode_whenSocialLogin_thenDefaultsToTZ() {
        SocialLoginRequestDto request =
                SocialLoginRequestDto.builder().provider("GOOGLE").idToken("valid-token").build();

        when(authUserLinkRepository.findByProviderAndProviderUserId("GOOGLE", GOOGLE_SUBJECT))
                .thenReturn(Optional.empty());
        when(authUserRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());
        when(authUserRepository.save(any(AuthUser.class)))
                .thenAnswer(
                        inv -> {
                            AuthUser u = inv.getArgument(0);
                            u.setId(USER_ID);
                            return u;
                        });
        when(tokenService.issueTokens(any(), eq(true)))
                .thenReturn(TokenResponseDto.builder().isNewUser(true).build());

        socialLoginService.socialLogin(request);

        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserRepository).save(captor.capture());
        assertThat(captor.getValue().getCountryCode()).isEqualTo("TZ");
    }

    @Test
    void givenInvalidToken_whenSocialLogin_thenThrowsBadRequest() {
        SocialLoginService realService =
                new SocialLoginService(
                        authUserRepository,
                        authUserLinkRepository,
                        tokenService,
                        otpService,
                        kafkaTemplate,
                        authProperties) {
                    @Override
                    Jwt verifyIdToken(String provider, String idToken) {
                        throw new BadRequestException("Invalid GOOGLE ID token");
                    }
                };

        SocialLoginRequestDto request =
                SocialLoginRequestDto.builder().provider("GOOGLE").idToken("invalid-token").build();

        assertThatThrownBy(() -> realService.socialLogin(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid GOOGLE ID token");
    }

    @Test
    void givenValidOtp_whenLinkPhone_thenPhoneLinked() {
        AuthUser user = createExistingUser();
        user.setPhoneNumber(null);
        when(authUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        OtpCode otpCode = new OtpCode();
        otpCode.setPhoneNumber("+255712345678");
        when(otpService.verifyOtp("+255712345678", "123456")).thenReturn(otpCode);
        when(authUserRepository.findByPhoneNumber("+255712345678")).thenReturn(Optional.empty());

        LinkPhoneRequestDto request =
                LinkPhoneRequestDto.builder()
                        .phoneNumber("+255712345678")
                        .otp("123456")
                        .countryCode("TZ")
                        .build();

        socialLoginService.linkPhone(USER_ID, request);

        assertThat(user.getPhoneNumber()).isEqualTo("+255712345678");
        assertThat(user.isPhoneVerified()).isTrue();
        assertThat(user.getCountryCode()).isEqualTo("TZ");
        verify(authUserRepository).save(user);
    }

    @Test
    void givenPhoneAlreadyTaken_whenLinkPhone_thenThrowsConflict() {
        AuthUser user = createExistingUser();
        when(authUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        OtpCode otpCode = new OtpCode();
        otpCode.setPhoneNumber("+255712345678");
        when(otpService.verifyOtp("+255712345678", "123456")).thenReturn(otpCode);

        AuthUser otherUser = new AuthUser();
        otherUser.setId(UUID.randomUUID());
        when(authUserRepository.findByPhoneNumber("+255712345678"))
                .thenReturn(Optional.of(otherUser));

        LinkPhoneRequestDto request =
                LinkPhoneRequestDto.builder().phoneNumber("+255712345678").otp("123456").build();

        assertThatThrownBy(() -> socialLoginService.linkPhone(USER_ID, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already linked");
    }

    @Test
    void givenUserNotFound_whenLinkPhone_thenThrowsNotFound() {
        when(authUserRepository.findById(USER_ID)).thenReturn(Optional.empty());

        LinkPhoneRequestDto request =
                LinkPhoneRequestDto.builder().phoneNumber("+255712345678").otp("123456").build();

        assertThatThrownBy(() -> socialLoginService.linkPhone(USER_ID, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void givenValidSocialToken_whenLinkSocial_thenLinkCreated() {
        AuthUser user = createExistingUser();
        user.setPhoneNumber("+255712345678");
        user.setEmail(null);
        user.setProfilePhotoUrl(null);
        user.setFullName(null);
        when(authUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(authUserLinkRepository.findByProviderAndProviderUserId("GOOGLE", GOOGLE_SUBJECT))
                .thenReturn(Optional.empty());
        when(authUserLinkRepository.existsByUserIdAndProvider(USER_ID, "GOOGLE")).thenReturn(false);

        LinkSocialRequestDto request =
                LinkSocialRequestDto.builder().provider("GOOGLE").idToken("valid-token").build();

        socialLoginService.linkSocial(USER_ID, request);

        verify(authUserLinkRepository).save(any(AuthUserLink.class));
        // Verify user email/photo was updated from social profile
        assertThat(user.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(user.getProfilePhotoUrl()).isEqualTo(TEST_PHOTO);
        assertThat(user.getFullName()).isEqualTo(TEST_NAME);
        verify(authUserRepository).save(user);
    }

    @Test
    void givenSocialAlreadyLinkedToOtherUser_whenLinkSocial_thenThrowsConflict() {
        AuthUser user = createExistingUser();
        when(authUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        AuthUserLink otherLink = new AuthUserLink();
        otherLink.setUserId(UUID.randomUUID());
        when(authUserLinkRepository.findByProviderAndProviderUserId("GOOGLE", GOOGLE_SUBJECT))
                .thenReturn(Optional.of(otherLink));

        LinkSocialRequestDto request =
                LinkSocialRequestDto.builder().provider("GOOGLE").idToken("valid-token").build();

        assertThatThrownBy(() -> socialLoginService.linkSocial(USER_ID, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already linked to another user");
    }

    @Test
    void givenProviderAlreadyLinked_whenLinkSocial_thenThrowsConflict() {
        AuthUser user = createExistingUser();
        when(authUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(authUserLinkRepository.findByProviderAndProviderUserId("GOOGLE", GOOGLE_SUBJECT))
                .thenReturn(Optional.empty());
        when(authUserLinkRepository.existsByUserIdAndProvider(USER_ID, "GOOGLE")).thenReturn(true);

        LinkSocialRequestDto request =
                LinkSocialRequestDto.builder().provider("GOOGLE").idToken("valid-token").build();

        assertThatThrownBy(() -> socialLoginService.linkSocial(USER_ID, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already have a GOOGLE account linked");
    }

    @Test
    void givenAppleProvider_whenSocialLogin_thenExtractsNameCorrectly() {
        SocialLoginService appleService =
                new SocialLoginService(
                        authUserRepository,
                        authUserLinkRepository,
                        tokenService,
                        otpService,
                        kafkaTemplate,
                        authProperties) {
                    @Override
                    Jwt verifyIdToken(String provider, String idToken) {
                        Map<String, Object> headers = Map.of("alg", "RS256");
                        Map<String, Object> claims = new HashMap<>();
                        claims.put("sub", "apple-user-456");
                        claims.put("email", "apple@example.com");
                        claims.put("given_name", "Jane");
                        claims.put("family_name", "Doe");
                        claims.put("aud", List.of("test-apple-client-id"));
                        return new Jwt(
                                "token",
                                Instant.now(),
                                Instant.now().plusSeconds(3600),
                                headers,
                                claims);
                    }
                };

        when(authUserLinkRepository.findByProviderAndProviderUserId("APPLE", "apple-user-456"))
                .thenReturn(Optional.empty());
        when(authUserRepository.findByEmail("apple@example.com")).thenReturn(Optional.empty());
        when(authUserRepository.save(any(AuthUser.class)))
                .thenAnswer(
                        inv -> {
                            AuthUser u = inv.getArgument(0);
                            u.setId(USER_ID);
                            return u;
                        });
        when(tokenService.issueTokens(any(), eq(true)))
                .thenReturn(TokenResponseDto.builder().isNewUser(true).build());

        SocialLoginRequestDto request =
                SocialLoginRequestDto.builder()
                        .provider("APPLE")
                        .idToken("apple-token")
                        .countryCode("TZ")
                        .build();

        appleService.socialLogin(request);

        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserRepository).save(captor.capture());
        assertThat(captor.getValue().getFullName()).isEqualTo("Jane Doe");
        assertThat(captor.getValue().getProfilePhotoUrl()).isNull(); // Apple doesn't provide photos
        assertThat(captor.getValue().getAuthProvider()).isEqualTo("APPLE");
    }

    @Test
    void givenSameUserLink_whenLinkSocial_thenThrowsConflict() {
        AuthUser user = createExistingUser();
        when(authUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        AuthUserLink sameUserLink = new AuthUserLink();
        sameUserLink.setUserId(USER_ID);
        when(authUserLinkRepository.findByProviderAndProviderUserId("GOOGLE", GOOGLE_SUBJECT))
                .thenReturn(Optional.of(sameUserLink));

        LinkSocialRequestDto request =
                LinkSocialRequestDto.builder().provider("GOOGLE").idToken("valid-token").build();

        assertThatThrownBy(() -> socialLoginService.linkSocial(USER_ID, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already linked");
    }

    @Test
    void givenExistingLinkedUser_whenLinkedUserNotFound_thenThrowsNotFound() {
        SocialLoginRequestDto request =
                SocialLoginRequestDto.builder().provider("GOOGLE").idToken("valid-token").build();

        AuthUserLink existingLink = new AuthUserLink();
        existingLink.setUserId(USER_ID);
        when(authUserLinkRepository.findByProviderAndProviderUserId("GOOGLE", GOOGLE_SUBJECT))
                .thenReturn(Optional.of(existingLink));
        when(authUserRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> socialLoginService.socialLogin(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Linked user not found");
    }

    @Test
    void givenUserNotFound_whenLinkSocial_thenThrowsNotFound() {
        when(authUserRepository.findById(USER_ID)).thenReturn(Optional.empty());

        LinkSocialRequestDto request =
                LinkSocialRequestDto.builder().provider("GOOGLE").idToken("valid-token").build();

        assertThatThrownBy(() -> socialLoginService.linkSocial(USER_ID, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void givenExistingUserEmailAndPhoto_whenLinkSocial_thenDoesNotOverwrite() {
        AuthUser user = createExistingUser();
        user.setEmail("existing@example.com");
        user.setProfilePhotoUrl("https://existing-photo.com/pic.jpg");
        user.setFullName("Existing Name");
        when(authUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(authUserLinkRepository.findByProviderAndProviderUserId("GOOGLE", GOOGLE_SUBJECT))
                .thenReturn(Optional.empty());
        when(authUserLinkRepository.existsByUserIdAndProvider(USER_ID, "GOOGLE")).thenReturn(false);

        LinkSocialRequestDto request =
                LinkSocialRequestDto.builder().provider("GOOGLE").idToken("valid-token").build();

        socialLoginService.linkSocial(USER_ID, request);

        // Existing values should NOT be overwritten
        assertThat(user.getEmail()).isEqualTo("existing@example.com");
        assertThat(user.getProfilePhotoUrl()).isEqualTo("https://existing-photo.com/pic.jpg");
        assertThat(user.getFullName()).isEqualTo("Existing Name");
    }

    private AuthUser createExistingUser() {
        AuthUser user = new AuthUser();
        user.setId(USER_ID);
        user.setCountryCode("TZ");
        user.setRole(UserRole.RIDER);
        user.setActive(true);
        user.setAuthProvider("PHONE");
        return user;
    }

    /**
     * Test subclass that overrides verifyIdToken to return a mock JWT without hitting real JWKS
     * endpoints.
     */
    private class TestSocialLoginService extends SocialLoginService {

        public TestSocialLoginService(
                AuthUserRepository authUserRepository,
                AuthUserLinkRepository authUserLinkRepository,
                TokenService tokenService,
                OtpService otpService,
                KafkaTemplate<String, Object> kafkaTemplate,
                AuthProperties authProperties) {
            super(
                    authUserRepository,
                    authUserLinkRepository,
                    tokenService,
                    otpService,
                    kafkaTemplate,
                    authProperties);
        }

        @Override
        Jwt verifyIdToken(String provider, String idToken) {
            Map<String, Object> headers = Map.of("alg", "RS256");
            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", GOOGLE_SUBJECT);
            claims.put("email", TEST_EMAIL);
            claims.put("name", TEST_NAME);
            claims.put("picture", TEST_PHOTO);
            claims.put("aud", List.of("test-google-client-id"));
            return new Jwt(
                    idToken, Instant.now(), Instant.now().plusSeconds(3600), headers, claims);
        }
    }
}
