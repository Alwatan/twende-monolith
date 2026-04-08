package tz.co.twende.notification.kafka;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.event.subscription.SubscriptionExpiredEvent;
import tz.co.twende.notification.kafka.handler.SubscriptionNotificationHandler;
import tz.co.twende.notification.service.NotificationService;
import tz.co.twende.notification.service.TemplateResolver;

@ExtendWith(MockitoExtension.class)
class SubscriptionNotificationHandlerTest {

    @Mock private NotificationService notificationService;
    @Mock private TemplateResolver templateResolver;

    @InjectMocks private SubscriptionNotificationHandler handler;

    @Test
    void givenSubscriptionExpiredEvent_whenProcessed_thenPushSentToDriver() {
        SubscriptionExpiredEvent event = new SubscriptionExpiredEvent();
        event.setSubscriptionId(UUID.randomUUID());
        event.setDriverId(UUID.randomUUID());
        event.setCountryCode("TZ");

        when(templateResolver.resolveTemplate(
                        eq("subscription.expired.driver"), eq("sw-TZ"), any()))
                .thenReturn("Pakiti yako imeisha.");

        handler.handle(event);

        verify(notificationService)
                .sendPush(
                        eq("TZ"),
                        eq(event.getDriverId()),
                        eq("Bundle Expired"),
                        eq("Pakiti yako imeisha."),
                        any(Map.class),
                        eq("subscription.expired.driver"));
    }
}
