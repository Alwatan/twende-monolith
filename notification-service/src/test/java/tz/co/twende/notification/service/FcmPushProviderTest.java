package tz.co.twende.notification.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.notification.provider.push.FcmPushProvider;
import tz.co.twende.notification.repository.FcmTokenRepository;

@ExtendWith(MockitoExtension.class)
class FcmPushProviderTest {

    @Mock private FcmTokenRepository fcmTokenRepository;

    @InjectMocks private FcmPushProvider fcmPushProvider;

    @Test
    void givenFcmProvider_whenGetId_thenReturnFcm() {
        assertThat(fcmPushProvider.getId()).isEqualTo("fcm");
    }

    @Test
    void givenNoFirebaseInit_whenSendNotification_thenSkipped() {
        // Firebase is not initialized in test, so it should skip
        UUID userId = UUID.randomUUID();
        fcmPushProvider.sendNotification(userId, "Title", "Body", Map.of());

        // Should not even look up tokens since Firebase isn't initialized
        verify(fcmTokenRepository, never())
                .findFirstByUserIdAndActiveTrueOrderByCreatedAtDesc(any());
    }

    @Test
    void givenNoFirebaseInit_whenSendData_thenSkipped() {
        UUID userId = UUID.randomUUID();
        fcmPushProvider.sendData(userId, Map.of("type", "TEST"));

        verify(fcmTokenRepository, never())
                .findFirstByUserIdAndActiveTrueOrderByCreatedAtDesc(any());
    }
}
