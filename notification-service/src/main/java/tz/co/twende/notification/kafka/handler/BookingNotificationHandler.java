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
        boolean isCargo = "CARGO".equals(event.getServiceCategory());

        String fareStr =
                event.getEstimatedFare() != null ? event.getEstimatedFare().toPlainString() : "0";
        String dateStr =
                event.getScheduledPickupAt() != null
                        ? event.getScheduledPickupAt().toString()
                        : "TBD";

        String templateKey = isCargo ? "cargo.confirmed" : "booking.confirmed";
        String title = isCargo ? "Cargo Booking Confirmed" : "Booking Confirmed";
        String notificationType = isCargo ? "CARGO_BOOKING_CONFIRMED" : "BOOKING_CONFIRMED";

        String body =
                templateResolver.resolveTemplate(
                        templateKey,
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
                title,
                body,
                Map.of("type", notificationType, "bookingId", event.getBookingId().toString()),
                templateKey);
    }

    public void handleBookingCompleted(BookingCompletedEvent event) {
        log.debug("Handling booking completed: {}", event.getBookingId());

        String countryCode = event.getCountryCode();
        String locale = resolveLocale(countryCode);
        boolean isCargo = "CARGO".equals(event.getServiceCategory());

        String fareStr = event.getFinalFare() != null ? event.getFinalFare().toPlainString() : "0";

        String templateKey = isCargo ? "cargo.completed" : "charter.completed";
        String title = isCargo ? "Cargo Delivered" : "Charter Completed";
        String notificationType = isCargo ? "CARGO_COMPLETED" : "CHARTER_COMPLETED";

        String body =
                templateResolver.resolveTemplate(
                        templateKey,
                        locale,
                        Map.of("fare", fareStr, "amount", fareStr, "currency", "TSh"));

        notificationService.sendPush(
                countryCode,
                event.getRiderId(),
                title,
                body,
                Map.of("type", notificationType, "bookingId", event.getBookingId().toString()),
                templateKey);
    }

    private String resolveLocale(String countryCode) {
        if ("TZ".equals(countryCode)) return "sw-TZ";
        return "en";
    }
}
