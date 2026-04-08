package tz.co.twende.notification.provider.push;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tz.co.twende.notification.provider.PushProvider;
import tz.co.twende.notification.repository.FcmTokenRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class FcmPushProvider implements PushProvider {

    private final FcmTokenRepository fcmTokenRepository;

    @Override
    public String getId() {
        return "fcm";
    }

    @Override
    public void sendNotification(UUID userId, String title, String body, Map<String, String> data) {
        if (!isFirebaseInitialized()) {
            log.warn("Firebase not initialized, skipping push to user {}", userId);
            return;
        }

        String token = resolveToken(userId);
        if (token == null) {
            log.warn("No active FCM token for user {}", userId);
            return;
        }

        try {
            Message.Builder builder =
                    Message.builder()
                            .setToken(token)
                            .setNotification(
                                    Notification.builder().setTitle(title).setBody(body).build());

            if (data != null && !data.isEmpty()) {
                builder.putAllData(data);
            }

            String response = FirebaseMessaging.getInstance().send(builder.build());
            log.info("Push notification sent to user {}: {}", userId, response);
        } catch (FirebaseMessagingException e) {
            handleFcmError(userId, token, e);
            throw new RuntimeException("Push notification failed", e);
        }
    }

    @Override
    public void sendData(UUID userId, Map<String, String> data) {
        if (!isFirebaseInitialized()) {
            log.warn("Firebase not initialized, skipping data push to user {}", userId);
            return;
        }

        String token = resolveToken(userId);
        if (token == null) {
            log.warn("No active FCM token for user {}", userId);
            return;
        }

        try {
            Message message = Message.builder().setToken(token).putAllData(data).build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Data push sent to user {}: {}", userId, response);
        } catch (FirebaseMessagingException e) {
            handleFcmError(userId, token, e);
            throw new RuntimeException("Data push failed", e);
        }
    }

    private String resolveToken(UUID userId) {
        return fcmTokenRepository
                .findFirstByUserIdAndActiveTrueOrderByCreatedAtDesc(userId)
                .map(tz.co.twende.notification.entity.FcmToken::getToken)
                .orElse(null);
    }

    private boolean isFirebaseInitialized() {
        try {
            FirebaseApp.getInstance();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private void handleFcmError(UUID userId, String token, FirebaseMessagingException e) {
        log.error("FCM error for user {}: {}", userId, e.getMessage());
        MessagingErrorCode errorCode = e.getMessagingErrorCode();
        if (errorCode == MessagingErrorCode.UNREGISTERED
                || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
            fcmTokenRepository
                    .findByUserIdAndToken(userId, token)
                    .ifPresent(
                            fcmToken -> {
                                fcmToken.setActive(false);
                                fcmTokenRepository.save(fcmToken);
                                log.info("Deactivated invalid FCM token for user {}", userId);
                            });
        }
    }
}
