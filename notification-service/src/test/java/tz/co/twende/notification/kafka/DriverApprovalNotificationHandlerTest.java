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
import tz.co.twende.notification.kafka.handler.DriverApprovalNotificationHandler;
import tz.co.twende.notification.service.NotificationService;
import tz.co.twende.notification.service.TemplateResolver;

@ExtendWith(MockitoExtension.class)
class DriverApprovalNotificationHandlerTest {

    @Mock private NotificationService notificationService;
    @Mock private TemplateResolver templateResolver;

    @InjectMocks private DriverApprovalNotificationHandler handler;

    @Test
    void givenDriverApproved_whenProcessed_thenPushSentToDriver() {
        UUID driverId = UUID.randomUUID();
        String countryCode = "TZ";

        when(templateResolver.resolveTemplate(eq("driver.approved"), eq("sw-TZ"), any()))
                .thenReturn("Hongera! Akaunti yako imeidhinishwa.");

        handler.handle(driverId, countryCode);

        verify(notificationService)
                .sendPush(
                        eq("TZ"),
                        eq(driverId),
                        eq("Account Approved"),
                        eq("Hongera! Akaunti yako imeidhinishwa."),
                        any(Map.class),
                        eq("driver.approved"));
    }

    @Test
    void givenKenyaCountryCode_whenProcessed_thenEnglishLocaleUsed() {
        UUID driverId = UUID.randomUUID();
        String countryCode = "KE";

        when(templateResolver.resolveTemplate(eq("driver.approved"), eq("en"), any()))
                .thenReturn("Congratulations!");

        handler.handle(driverId, countryCode);

        verify(notificationService)
                .sendPush(
                        eq("KE"),
                        eq(driverId),
                        eq("Account Approved"),
                        eq("Congratulations!"),
                        any(Map.class),
                        eq("driver.approved"));
    }
}
