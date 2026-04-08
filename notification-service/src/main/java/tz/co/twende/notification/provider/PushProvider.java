package tz.co.twende.notification.provider;

import java.util.Map;
import java.util.UUID;

public interface PushProvider {

    String getId();

    void sendNotification(UUID userId, String title, String body, Map<String, String> data);

    void sendData(UUID userId, Map<String, String> data);
}
