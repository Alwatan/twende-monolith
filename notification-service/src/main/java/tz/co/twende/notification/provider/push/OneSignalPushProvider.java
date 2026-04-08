package tz.co.twende.notification.provider.push;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tz.co.twende.notification.provider.PushProvider;

@Component
public class OneSignalPushProvider implements PushProvider {

    @Override
    public String getId() {
        return "onesignal";
    }

    @Override
    public void sendNotification(UUID userId, String title, String body, Map<String, String> data) {
        throw new UnsupportedOperationException("OneSignal push provider not yet implemented");
    }

    @Override
    public void sendData(UUID userId, Map<String, String> data) {
        throw new UnsupportedOperationException("OneSignal push provider not yet implemented");
    }
}
