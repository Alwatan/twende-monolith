package tz.co.twende.auth.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.auth.repository.OtpCodeRepository;
import tz.co.twende.auth.repository.RevokedTokenRepository;

@Service
@Slf4j
@RequiredArgsConstructor
public class CleanupService {

    private final OtpCodeRepository otpCodeRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    /**
     * Daily cleanup of expired OTP codes and revoked tokens. Runs at 03:00 AM UTC every day.
     * Removes OTP codes that have expired and revoked tokens whose original expiry has passed
     * (meaning the token would be invalid anyway, so no need to keep the blocklist entry).
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpired() {
        Instant now = Instant.now();

        otpCodeRepository.deleteByExpiresAtBefore(now);
        log.info("Cleaned up expired OTP codes");

        revokedTokenRepository.deleteByExpiresAtBefore(now);
        log.info("Cleaned up expired revoked tokens");
    }
}
