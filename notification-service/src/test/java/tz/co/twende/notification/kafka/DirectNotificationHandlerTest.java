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
import tz.co.twende.common.enums.NotificationType;
import tz.co.twende.common.event.notification.SendNotificationEvent;
import tz.co.twende.notification.kafka.handler.DirectNotificationHandler;
import tz.co.twende.notification.service.NotificationService;
import tz.co.twende.notification.service.TemplateResolver;

@ExtendWith(MockitoExtension.class)
class DirectNotificationHandlerTest {

    @Mock private NotificationService notificationService;
    @Mock private TemplateResolver templateResolver;

    @InjectMocks private DirectNotificationHandler handler;

    @Test
    void givenPushNotificationEvent_whenProcessed_thenPushSent() {
        SendNotificationEvent event = new SendNotificationEvent();
        event.setRecipientUserId(UUID.randomUUID());
        event.setType(NotificationType.PUSH);
        event.setTitleKey("test.title");
        event.setBodyKey("test.body");
        event.setTemplateParams(Map.of("name", "Test"));
        event.setData(Map.of("type", "TEST"));
        event.setCountryCode("TZ");

        when(templateResolver.resolveTemplate(eq("test.title"), eq("sw-TZ"), any()))
                .thenReturn("Test Title");
        when(templateResolver.resolveTemplate(eq("test.body"), eq("sw-TZ"), any()))
                .thenReturn("Test Body");

        handler.handle(event);

        verify(notificationService)
                .sendPush(
                        eq("TZ"),
                        eq(event.getRecipientUserId()),
                        eq("Test Title"),
                        eq("Test Body"),
                        eq(Map.of("type", "TEST")),
                        eq("test.body"));
    }

    @Test
    void givenSmsNotificationEvent_whenProcessed_thenSmsSent() {
        SendNotificationEvent event = new SendNotificationEvent();
        event.setRecipientUserId(UUID.randomUUID());
        event.setType(NotificationType.SMS);
        event.setBodyKey("test.sms.body");
        event.setTemplateParams(Map.of("code", "1234"));
        event.setCountryCode("TZ");

        when(templateResolver.resolveTemplate(eq("test.sms.body"), eq("sw-TZ"), any()))
                .thenReturn("Your code is 1234");

        handler.handle(event);

        verify(notificationService)
                .sendSms(
                        eq("TZ"),
                        eq(event.getRecipientUserId()),
                        isNull(),
                        eq("Your code is 1234"),
                        eq("test.sms.body"));
    }
}
