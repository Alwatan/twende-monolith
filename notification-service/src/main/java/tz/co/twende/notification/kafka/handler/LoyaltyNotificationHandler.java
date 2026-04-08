package tz.co.twende.notification.kafka.handler;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.loyalty.FreeRideOfferEarnedEvent;
import tz.co.twende.notification.service.NotificationService;
import tz.co.twende.notification.service.TemplateResolver;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoyaltyNotificationHandler {

    private final NotificationService notificationService;
    private final TemplateResolver templateResolver;

    public void handle(FreeRideOfferEarnedEvent event) {
        log.debug("Handling free ride offer earned: rider={}", event.getRiderId());

        String countryCode = event.getCountryCode();
        String locale = resolveLocale(countryCode);

        String body =
                templateResolver.resolveTemplate(
                        "loyalty.offer.earned",
                        locale,
                        Map.of("vehicleType", event.getVehicleType().name(), "validDays", "30"));

        notificationService.sendPush(
                countryCode,
                event.getRiderId(),
                "Free Ride Earned!",
                body,
                Map.of(
                        "type", "FREE_RIDE_EARNED",
                        "offerId", event.getOfferId().toString(),
                        "vehicleType", event.getVehicleType().name()),
                "loyalty.offer.earned");
    }

    private String resolveLocale(String countryCode) {
        if ("TZ".equals(countryCode)) return "sw-TZ";
        return "en";
    }
}
