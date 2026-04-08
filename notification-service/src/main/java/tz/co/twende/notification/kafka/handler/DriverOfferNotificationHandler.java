package tz.co.twende.notification.kafka.handler;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.driver.DriverOfferNotificationEvent;
import tz.co.twende.notification.service.NotificationService;
import tz.co.twende.notification.service.TemplateResolver;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverOfferNotificationHandler {

    private final NotificationService notificationService;
    private final TemplateResolver templateResolver;

    public void handle(DriverOfferNotificationEvent event) {
        log.debug(
                "Handling driver offer notification: ride={}, driver={}",
                event.getRideId(),
                event.getDriverId());

        String countryCode = event.getCountryCode();
        String locale = resolveLocale(countryCode);

        String body =
                templateResolver.resolveTemplate(
                        "driver.offer",
                        locale,
                        Map.of(
                                "distanceKm",
                                String.format("%.1f", event.getEstimatedTripDistanceKm()),
                                "fare",
                                event.getTotalFare().toPlainString(),
                                "currency",
                                event.getCurrencyCode() != null ? event.getCurrencyCode() : "TSh"));

        notificationService.sendPush(
                countryCode,
                event.getDriverId(),
                "New Ride Offer",
                body,
                Map.of(
                        "type", "RIDE_OFFER",
                        "rideId", event.getRideId().toString(),
                        "fare", event.getTotalFare().toPlainString(),
                        "offerWindowSeconds", String.valueOf(event.getOfferWindowSeconds())),
                "driver.offer");
    }

    private String resolveLocale(String countryCode) {
        if ("TZ".equals(countryCode)) return "sw-TZ";
        return "en";
    }
}
