package tz.co.twende.notification.kafka;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.event.ride.BookingCompletedEvent;
import tz.co.twende.common.event.ride.BookingRequestedEvent;
import tz.co.twende.notification.kafka.handler.BookingNotificationHandler;
import tz.co.twende.notification.service.NotificationService;
import tz.co.twende.notification.service.TemplateResolver;

@ExtendWith(MockitoExtension.class)
class BookingNotificationHandlerTest {

    @Mock private NotificationService notificationService;
    @Mock private TemplateResolver templateResolver;

    @InjectMocks private BookingNotificationHandler handler;

    @Test
    void givenBookingRequestedEvent_whenProcessed_thenPushSentToCustomer() {
        BookingRequestedEvent event = new BookingRequestedEvent();
        event.setBookingId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());
        event.setVehicleType("MINIBUS_STANDARD");
        event.setScheduledPickupAt(Instant.now().plus(2, ChronoUnit.DAYS));
        event.setEstimatedFare(new BigDecimal("50000"));
        event.setCountryCode("TZ");

        when(templateResolver.resolveTemplate(eq("booking.confirmed"), eq("sw-TZ"), any()))
                .thenReturn("Nafasi yako imethibitishwa kwa MINIBUS_STANDARD. Nauli: TSh 50000.");

        handler.handleBookingRequested(event);

        verify(notificationService)
                .sendPush(
                        eq("TZ"),
                        eq(event.getRiderId()),
                        eq("Booking Confirmed"),
                        anyString(),
                        any(),
                        eq("booking.confirmed"));
    }

    @Test
    void givenBookingCompletedEvent_whenProcessed_thenPushSentToCustomer() {
        BookingCompletedEvent event = new BookingCompletedEvent();
        event.setBookingId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());
        event.setDriverId(UUID.randomUUID());
        event.setFinalFare(new BigDecimal("52000"));
        event.setCountryCode("TZ");

        when(templateResolver.resolveTemplate(eq("charter.completed"), eq("sw-TZ"), any()))
                .thenReturn("Safari ya charter imekamilika. Umelipa TSh 52000.");

        handler.handleBookingCompleted(event);

        verify(notificationService)
                .sendPush(
                        eq("TZ"),
                        eq(event.getRiderId()),
                        eq("Charter Completed"),
                        anyString(),
                        any(),
                        eq("charter.completed"));
    }

    @Test
    void givenNonTzCountryCode_whenProcessed_thenUsesEnglishLocale() {
        BookingRequestedEvent event = new BookingRequestedEvent();
        event.setBookingId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());
        event.setVehicleType("MINIBUS_STANDARD");
        event.setScheduledPickupAt(Instant.now().plus(1, ChronoUnit.DAYS));
        event.setEstimatedFare(new BigDecimal("100000"));
        event.setCountryCode("KE");

        when(templateResolver.resolveTemplate(eq("booking.confirmed"), eq("en"), any()))
                .thenReturn("Your booking is confirmed.");

        handler.handleBookingRequested(event);

        verify(templateResolver).resolveTemplate(eq("booking.confirmed"), eq("en"), any());
    }
}
