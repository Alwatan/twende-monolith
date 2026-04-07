package tz.co.twende.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import tz.co.twende.auth.config.AuthProperties;
import tz.co.twende.auth.entity.OtpCode;
import tz.co.twende.auth.exception.TooManyRequestsException;
import tz.co.twende.auth.repository.OtpCodeRepository;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock private OtpCodeRepository otpCodeRepository;

    @Mock private RedisTemplate<String, String> redisTemplate;

    @Mock private ValueOperations<String, String> valueOperations;

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private OtpService otpService;

    private AuthProperties authProperties;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        AuthProperties.Otp otp = new AuthProperties.Otp();
        otp.setDevMode(true);
        otp.setExpiryMinutes(5);
        otp.setMaxAttempts(3);
        otp.setMaxRequestsPerWindow(3);
        otp.setWindowMinutes(10);
        otp.setLength(6);
        authProperties.setOtp(otp);

        // Re-create OtpService with the real AuthProperties instance since @InjectMocks
        // would have injected the mock. We need to reconstruct it manually.
        otpService =
                new OtpService(otpCodeRepository, redisTemplate, kafkaTemplate, authProperties);
    }

    @Test
    void givenValidPhone_whenRequestOtp_thenSavesOtpAndLogsInDevMode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("otp:rate:+255712345678")).thenReturn(1L);
        when(redisTemplate.expire("otp:rate:+255712345678", 10, TimeUnit.MINUTES)).thenReturn(true);

        otpService.requestOtp("+255712345678", "TZ");

        ArgumentCaptor<OtpCode> captor = ArgumentCaptor.forClass(OtpCode.class);
        verify(otpCodeRepository).save(captor.capture());

        OtpCode saved = captor.getValue();
        assertThat(saved.getPhoneNumber()).isEqualTo("+255712345678");
        assertThat(saved.getCode()).hasSize(6);
        assertThat(saved.isUsed()).isFalse();
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        assertThat(saved.getCountryCode()).isEqualTo("TZ");

        // In dev mode, no Kafka event should be sent
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void givenRateLimitExceeded_whenRequestOtp_thenThrowsTooManyRequests() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("otp:rate:+255712345678")).thenReturn(4L);

        assertThatThrownBy(() -> otpService.requestOtp("+255712345678", "TZ"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Too many OTP requests");

        verify(otpCodeRepository, never()).save(any());
    }

    @Test
    void givenNullPhone_whenRequestOtp_thenThrowsBadRequest() {
        assertThatThrownBy(() -> otpService.requestOtp(null, "TZ"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Phone number is required");

        verify(otpCodeRepository, never()).save(any());
    }

    @Test
    void givenValidCode_whenVerifyOtp_thenReturnsOtpCode() {
        OtpCode otpCode = new OtpCode();
        otpCode.setPhoneNumber("+255712345678");
        otpCode.setCode("123456");
        otpCode.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        otpCode.setUsed(false);
        otpCode.setAttempts(0);

        when(otpCodeRepository.findTopByPhoneNumberAndUsedFalseOrderByCreatedAtDesc(
                        "+255712345678"))
                .thenReturn(Optional.of(otpCode));

        OtpCode result = otpService.verifyOtp("+255712345678", "123456");

        assertThat(result).isNotNull();
        assertThat(result.isUsed()).isTrue();
        assertThat(result.getAttempts()).isEqualTo(1);
        verify(otpCodeRepository, times(2)).save(otpCode);
    }

    @Test
    void givenWrongCode_whenVerifyOtp_thenThrowsBadRequestWithRemainingAttempts() {
        OtpCode otpCode = new OtpCode();
        otpCode.setPhoneNumber("+255712345678");
        otpCode.setCode("123456");
        otpCode.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        otpCode.setUsed(false);
        otpCode.setAttempts(0);

        when(otpCodeRepository.findTopByPhoneNumberAndUsedFalseOrderByCreatedAtDesc(
                        "+255712345678"))
                .thenReturn(Optional.of(otpCode));

        assertThatThrownBy(() -> otpService.verifyOtp("+255712345678", "999999"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("2 attempt(s) remaining");

        assertThat(otpCode.getAttempts()).isEqualTo(1);
        assertThat(otpCode.isUsed()).isFalse();
        verify(otpCodeRepository, times(1)).save(otpCode);
    }

    @Test
    void givenExpiredCode_whenVerifyOtp_thenThrowsBadRequest() {
        OtpCode otpCode = new OtpCode();
        otpCode.setPhoneNumber("+255712345678");
        otpCode.setCode("123456");
        otpCode.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        otpCode.setUsed(false);
        otpCode.setAttempts(0);

        when(otpCodeRepository.findTopByPhoneNumberAndUsedFalseOrderByCreatedAtDesc(
                        "+255712345678"))
                .thenReturn(Optional.of(otpCode));

        assertThatThrownBy(() -> otpService.verifyOtp("+255712345678", "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void givenMaxAttemptsReached_whenVerifyOtp_thenThrowsBadRequest() {
        OtpCode otpCode = new OtpCode();
        otpCode.setPhoneNumber("+255712345678");
        otpCode.setCode("123456");
        otpCode.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        otpCode.setUsed(false);
        otpCode.setAttempts(3);

        when(otpCodeRepository.findTopByPhoneNumberAndUsedFalseOrderByCreatedAtDesc(
                        "+255712345678"))
                .thenReturn(Optional.of(otpCode));

        assertThatThrownBy(() -> otpService.verifyOtp("+255712345678", "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Maximum verification attempts reached");
    }

    @Test
    void givenNoOtpFound_whenVerifyOtp_thenThrowsResourceNotFound() {
        when(otpCodeRepository.findTopByPhoneNumberAndUsedFalseOrderByCreatedAtDesc(
                        "+255712345678"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.verifyOtp("+255712345678", "123456"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No pending OTP found");
    }
}
