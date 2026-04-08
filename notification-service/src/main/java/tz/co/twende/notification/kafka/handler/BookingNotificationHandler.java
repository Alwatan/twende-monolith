package tz.co.twende.notification.kafka.handler;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.ride.BookingCompletedEvent;
import tz.co.twende.common.event.ride.BookingRequestedEvent;
import tz.co.twende.notification.service.NotificationService;
import tz.co.twende.notification.service.TemplateResolver;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingNotificationHandler {

    private final NotificationService notificationService;
    private final TemplateResolver templateResolver;

    public void handleBookingRequested(BookingRequestedEvent event) {
        log.debug("Handling booking requested: {}", event.getBookingId());

        String countryCode = event.getCountryCode();
        String locale = resolveLocale(countryCode);

        String fareStr =
                event.getEstimatedFare() != null ? event.getEstimatedFare().toPlainString() : "0";
        String dateStr =
                event.getScheduledPickupAt() != null
                        ? event.getScheduledPickupAt().toString()
                        : "TBD";

        String body =
                templateResolver.resolveTemplate(
                        "booking.confirmed",
                        locale,
                        Map.of(
                                "vehicleType",
                                event.getVehicleType(),
                                "date",
                                dateStr,
                                "fare",
                                fareStr,
                                "currency",
                                "TSh"));

        notificationService.sendPush(
                countryCode,
                event.getRiderId(),
                "Booking Confirmed",
                body,
                Map.of("type", "BOOKING_CONFIRMED", "bookingId", event.getBookingId().toString()),
                "booking.confirmed");
    }

    public void handleBookingCompleted(BookingCompletedEvent event) {
        log.debug("Handling booking completed: {}", event.getBookingId());

        String countryCode = event.getCountryCode();
        String locale = resolveLocale(countryCode);

        String fareStr = event.getFinalFare() != null ? event.getFinalFare().toPlainString() : "0";

        String body =
                templateResolver.resolveTemplate(
                        "charter.completed", locale, Map.of("fare", fareStr, "currency", "TSh"));

        notificationService.sendPush(
                countryCode,
                event.getRiderId(),
                "Charter Completed",
                body,
                Map.of("type", "CHARTER_COMPLETED", "bookingId", event.getBookingId().toString()),
                "charter.completed");
    }

    private String resolveLocale(String countryCode) {
        if ("TZ".equals(countryCode)) return "sw-TZ";
        return "en";
    }
}
