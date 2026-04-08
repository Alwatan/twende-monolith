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
import tz.co.twende.common.event.driver.DriverOfferNotificationEvent;
import tz.co.twende.notification.kafka.handler.DriverOfferNotificationHandler;
import tz.co.twende.notification.service.NotificationService;
import tz.co.twende.notification.service.TemplateResolver;

@ExtendWith(MockitoExtension.class)
class DriverOfferNotificationHandlerTest {

    @Mock private NotificationService notificationService;
    @Mock private TemplateResolver templateResolver;

    @InjectMocks private DriverOfferNotificationHandler handler;

    @Test
    void givenDriverOfferEvent_whenProcessed_thenPushSentToDriver() {
        DriverOfferNotificationEvent event = new DriverOfferNotificationEvent();
        event.setRideId(UUID.randomUUID());
        event.setDriverId(UUID.randomUUID());
        event.setEstimatedTripDistanceKm(3.5);
        event.setTotalFare(new BigDecimal("5000"));
        event.setCurrencyCode("TSh");
        event.setOfferWindowSeconds(15);
        event.setCountryCode("TZ");

        when(templateResolver.resolveTemplate(eq("driver.offer"), eq("sw-TZ"), any()))
                .thenReturn("Safari mpya: 3.5 km mbali. TSh 5000. Kukubali?");

        handler.handle(event);

        verify(notificationService)
                .sendPush(
                        eq("TZ"),
                        eq(event.getDriverId()),
                        eq("New Ride Offer"),
                        contains("Safari mpya"),
                        any(Map.class),
                        eq("driver.offer"));
    }
}
