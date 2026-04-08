package tz.co.twende.notification.kafka;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.enums.VehicleType;
import tz.co.twende.common.event.loyalty.FreeRideOfferEarnedEvent;
import tz.co.twende.notification.kafka.handler.LoyaltyNotificationHandler;
import tz.co.twende.notification.service.NotificationService;
import tz.co.twende.notification.service.TemplateResolver;

@ExtendWith(MockitoExtension.class)
class LoyaltyNotificationHandlerTest {

    @Mock private NotificationService notificationService;
    @Mock private TemplateResolver templateResolver;

    @InjectMocks private LoyaltyNotificationHandler handler;

    @Test
    void givenFreeRideOfferEvent_whenProcessed_thenPushSentToRider() {
        FreeRideOfferEarnedEvent event = new FreeRideOfferEarnedEvent();
        event.setOfferId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());
        event.setVehicleType(VehicleType.BAJAJ);
        event.setMaxDistanceKm(new BigDecimal("10"));
        event.setExpiresAt(Instant.now().plusSeconds(86400 * 30));
        event.setCountryCode("TZ");

        when(templateResolver.resolveTemplate(eq("loyalty.offer.earned"), eq("sw-TZ"), any()))
                .thenReturn("Umepata safari ya bure ya BAJAJ!");

        handler.handle(event);

        verify(notificationService)
                .sendPush(
                        eq("TZ"),
                        eq(event.getRiderId()),
                        eq("Free Ride Earned!"),
                        contains("BAJAJ"),
                        any(Map.class),
                        eq("loyalty.offer.earned"));
    }
}
