package tz.co.twende.notification.kafka;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.notification.kafka.handler.RideCompletedNotificationHandler;
import tz.co.twende.notification.service.NotificationService;
import tz.co.twende.notification.service.TemplateResolver;

@ExtendWith(MockitoExtension.class)
class RideCompletedNotificationHandlerTest {

    @Mock private NotificationService notificationService;
    @Mock private TemplateResolver templateResolver;

    @InjectMocks private RideCompletedNotificationHandler handler;

    @Test
    void givenRideCompletedEvent_whenProcessed_thenPushSentToRider() {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setRideId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());
        event.setDriverId(UUID.randomUUID());
        event.setFinalFare(new BigDecimal("5000"));
        event.setCountryCode("TZ");

        when(templateResolver.resolveTemplate(eq("ride.completed.rider"), eq("sw-TZ"), any()))
                .thenReturn("Safari imekamilika. Umelipa TSh 5000.");

        handler.handle(event);

        verify(notificationService)
                .sendPush(
                        eq("TZ"),
                        eq(event.getRiderId()),
                        eq("Trip Completed"),
                        eq("Safari imekamilika. Umelipa TSh 5000."),
                        any(Map.class),
                        eq("ride.completed.rider"));
    }
}
