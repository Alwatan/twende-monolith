package com.twende.auth.service;

import com.twende.auth.config.AuthProperties;
import com.twende.auth.entity.OtpCode;
import com.twende.auth.exception.TooManyRequestsException;
import com.twende.auth.repository.OtpCodeRepository;
import com.twende.common.enums.NotificationType;
import com.twende.common.event.notification.SendNotificationEvent;
import com.twende.common.exception.BadRequestException;
import com.twende.common.exception.ResourceNotFoundException;
import com.twende.common.util.OtpUtil;
import com.twende.common.util.PhoneUtil;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private static final String NOTIFICATION_TOPIC = "twende.notifications.send";

    private final OtpCodeRepository otpCodeRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuthProperties authProperties;

    /**
     * Request a new OTP for the given phone number.
     *
     * <p>Rate-limited via Redis INCR. In dev mode, OTP is logged to console instead of being sent
     * via SMS.
     */
    public void requestOtp(String phoneNumber, String countryCode) {
        String normalisedPhone = PhoneUtil.normalise(phoneNumber, countryCode);

        enforceRateLimit(normalisedPhone);

        String code = OtpUtil.generate6Digit();

        OtpCode otpCode = new OtpCode();
        otpCode.setCountryCode(countryCode);
        otpCode.setPhoneNumber(normalisedPhone);
        otpCode.setCode(code);
        otpCode.setExpiresAt(
                Instant.now().plus(authProperties.getOtp().getExpiryMinutes(), ChronoUnit.MINUTES));
        otpCode.setUsed(false);
        otpCode.setAttempts(0);
        otpCodeRepository.save(otpCode);

        if (authProperties.getOtp().isDevMode()) {
            log.info("DEV MODE OTP for {}: {}", normalisedPhone, code);
        } else {
            publishOtpNotification(normalisedPhone, code, countryCode);
        }
    }

    /**
     * Verify an OTP for the given phone number.
     *
     * @return the verified OtpCode entity
     * @throws ResourceNotFoundException if no pending OTP exists
     * @throws BadRequestException if OTP is expired, max attempts reached, or code is wrong
     */
    public OtpCode verifyOtp(String phoneNumber, String otp) {
        String normalisedPhone = PhoneUtil.normalise(phoneNumber, "TZ");

        OtpCode otpCode =
                otpCodeRepository
                        .findTopByPhoneNumberAndUsedFalseOrderByCreatedAtDesc(normalisedPhone)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "No pending OTP found for this phone number"));

        if (otpCode.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("OTP has expired. Please request a new one.");
        }

        int maxAttempts = authProperties.getOtp().getMaxAttempts();
        if (otpCode.getAttempts() >= maxAttempts) {
            throw new BadRequestException(
                    "Maximum verification attempts reached. Please request a new OTP.");
        }

        otpCode.setAttempts(otpCode.getAttempts() + 1);
        otpCodeRepository.save(otpCode);

        if (!otpCode.getCode().equals(otp)) {
            int remaining = maxAttempts - otpCode.getAttempts();
            throw new BadRequestException("Invalid OTP. " + remaining + " attempt(s) remaining.");
        }

        otpCode.setUsed(true);
        otpCodeRepository.save(otpCode);

        return otpCode;
    }

    private void enforceRateLimit(String phoneNumber) {
        String key = "otp:rate:" + phoneNumber;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, authProperties.getOtp().getWindowMinutes(), TimeUnit.MINUTES);
        }
        if (count != null && count > authProperties.getOtp().getMaxRequestsPerWindow()) {
            throw new TooManyRequestsException("Too many OTP requests. Please try again later.");
        }
    }

    private void publishOtpNotification(String phoneNumber, String code, String countryCode) {
        SendNotificationEvent event = new SendNotificationEvent();
        event.setEventType("SEND_NOTIFICATION");
        event.setCountryCode(countryCode);
        event.setType(NotificationType.SMS);
        event.setTitleKey("otp.sms.title");
        event.setBodyKey("otp.sms.body");
        event.setTemplateParams(Map.of("otp", code, "phoneNumber", phoneNumber));
        event.setData(Map.of("otp", code));

        kafkaTemplate.send(NOTIFICATION_TOPIC, phoneNumber, event);
        log.debug("Published OTP notification event for phone: {}", phoneNumber);
    }
}
