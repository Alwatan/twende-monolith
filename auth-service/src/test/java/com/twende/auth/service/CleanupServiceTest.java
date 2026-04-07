package com.twende.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.twende.auth.repository.OtpCodeRepository;
import com.twende.auth.repository.RevokedTokenRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CleanupServiceTest {

    @Mock private OtpCodeRepository otpCodeRepository;
    @Mock private RevokedTokenRepository revokedTokenRepository;
    @InjectMocks private CleanupService cleanupService;

    @Test
    void givenExpiredRecords_whenCleanup_thenDeletesBoth() {
        cleanupService.cleanupExpired();

        verify(otpCodeRepository).deleteByExpiresAtBefore(any(Instant.class));
        verify(revokedTokenRepository).deleteByExpiresAtBefore(any(Instant.class));
    }
}
