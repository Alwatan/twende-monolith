package tz.co.twende.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tz.co.twende.auth.dto.OtpVerifyDto;
import tz.co.twende.auth.dto.RegisterRequestDto;
import tz.co.twende.auth.dto.TokenResponseDto;
import tz.co.twende.auth.dto.UserInfoDto;
import tz.co.twende.auth.entity.AuthUser;
import tz.co.twende.auth.entity.OtpCode;
import tz.co.twende.auth.repository.AuthUserRepository;
import tz.co.twende.common.enums.UserRole;
import tz.co.twende.common.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthUserRepository authUserRepository;

    @Mock private OtpService otpService;

    @Mock private TokenService tokenService;

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private AuthService authService;

    @Test
    void givenExistingUser_whenAuthenticateWithOtp_thenReturnsTokens() {
        OtpVerifyDto request = new OtpVerifyDto("+255712345678", "123456");

        OtpCode verifiedOtp = new OtpCode();
        verifiedOtp.setPhoneNumber("+255712345678");
        verifiedOtp.setCountryCode("TZ");
        verifiedOtp.setCode("123456");
        verifiedOtp.setUsed(true);

        AuthUser existingUser = new AuthUser();
        existingUser.setId(UUID.randomUUID());
        existingUser.setPhoneNumber("+255712345678");
        existingUser.setCountryCode("TZ");
        existingUser.setRole(UserRole.RIDER);
        existingUser.setPhoneVerified(true);

        TokenResponseDto expectedTokens =
                TokenResponseDto.builder()
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .tokenType("Bearer")
                        .expiresIn(3600)
                        .isNewUser(false)
                        .build();

        when(otpService.verifyOtp("+255712345678", "123456")).thenReturn(verifiedOtp);
        when(authUserRepository.findByPhoneNumber("+255712345678"))
                .thenReturn(Optional.of(existingUser));
        when(tokenService.issueTokens(existingUser, false)).thenReturn(expectedTokens);

        TokenResponseDto result = authService.authenticateWithOtp(request);

        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.isNewUser()).isFalse();
        verify(authUserRepository, never()).save(any());
    }

    @Test
    void givenNewUser_whenAuthenticateWithOtp_thenCreatesUserAndReturnsTokens() {
        OtpVerifyDto request = new OtpVerifyDto("+255712345678", "123456");

        OtpCode verifiedOtp = new OtpCode();
        verifiedOtp.setPhoneNumber("+255712345678");
        verifiedOtp.setCountryCode("TZ");
        verifiedOtp.setCode("123456");
        verifiedOtp.setUsed(true);

        AuthUser savedUser = new AuthUser();
        savedUser.setId(UUID.randomUUID());
        savedUser.setPhoneNumber("+255712345678");
        savedUser.setCountryCode("TZ");
        savedUser.setRole(UserRole.RIDER);
        savedUser.setPhoneVerified(false);

        TokenResponseDto expectedTokens =
                TokenResponseDto.builder()
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .tokenType("Bearer")
                        .expiresIn(3600)
                        .isNewUser(true)
                        .build();

        when(otpService.verifyOtp("+255712345678", "123456")).thenReturn(verifiedOtp);
        when(authUserRepository.findByPhoneNumber("+255712345678")).thenReturn(Optional.empty());
        when(authUserRepository.save(any(AuthUser.class))).thenReturn(savedUser);
        when(tokenService.issueTokens(savedUser, true)).thenReturn(expectedTokens);

        TokenResponseDto result = authService.authenticateWithOtp(request);

        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.isNewUser()).isTrue();

        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserRepository).save(captor.capture());
        AuthUser newUser = captor.getValue();
        assertThat(newUser.getPhoneNumber()).isEqualTo("+255712345678");
        assertThat(newUser.getCountryCode()).isEqualTo("TZ");
        assertThat(newUser.getRole()).isEqualTo(UserRole.RIDER);
        assertThat(newUser.isPhoneVerified()).isFalse();
    }

    @Test
    void givenValidRequest_whenRegister_thenUpdatesUserAndPublishesEvent() {
        UUID userId = UUID.randomUUID();
        RegisterRequestDto request = new RegisterRequestDto("Amina Hassan", UserRole.RIDER, "TZ");

        AuthUser existingUser = new AuthUser();
        existingUser.setId(userId);
        existingUser.setPhoneNumber("+255712345678");
        existingUser.setCountryCode("TZ");
        existingUser.setRole(UserRole.RIDER);
        existingUser.setPhoneVerified(false);

        when(authUserRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        authService.register(userId, request);

        verify(authUserRepository).save(existingUser);
        assertThat(existingUser.getFullName()).isEqualTo("Amina Hassan");
        assertThat(existingUser.getRole()).isEqualTo(UserRole.RIDER);
        assertThat(existingUser.getCountryCode()).isEqualTo("TZ");
        assertThat(existingUser.isPhoneVerified()).isTrue();

        verify(kafkaTemplate).send(eq("twende.users.registered"), eq(userId.toString()), any());
    }

    @Test
    void givenUserNotFound_whenRegister_thenThrowsResourceNotFound() {
        UUID userId = UUID.randomUUID();
        RegisterRequestDto request = new RegisterRequestDto("Amina Hassan", UserRole.RIDER, "TZ");

        when(authUserRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(authUserRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void givenExistingUser_whenGetCurrentUser_thenReturnsUserInfo() {
        UUID userId = UUID.randomUUID();

        AuthUser user = new AuthUser();
        user.setId(userId);
        user.setPhoneNumber("+255712345678");
        user.setFullName("Amina Hassan");
        user.setRole(UserRole.RIDER);
        user.setCountryCode("TZ");
        user.setPhoneVerified(true);

        when(authUserRepository.findById(userId)).thenReturn(Optional.of(user));

        UserInfoDto result = authService.getCurrentUser(userId);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getPhoneNumber()).isEqualTo("+255712345678");
        assertThat(result.getFullName()).isEqualTo("Amina Hassan");
        assertThat(result.getRole()).isEqualTo("RIDER");
        assertThat(result.getCountryCode()).isEqualTo("TZ");
        assertThat(result.isPhoneVerified()).isTrue();
    }

    @Test
    void givenUserNotFound_whenGetCurrentUser_thenThrowsResourceNotFound() {
        UUID userId = UUID.randomUUID();

        when(authUserRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }
}
