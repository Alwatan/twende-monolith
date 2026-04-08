package tz.co.twende.notification.kafka.handler;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.notification.service.NotificationService;
import tz.co.twende.notification.service.TemplateResolver;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideCompletedNotificationHandler {

    private final NotificationService notificationService;
    private final TemplateResolver templateResolver;

    public void handle(RideCompletedEvent event) {
        log.debug("Handling ride completed: {}", event.getRideId());

        String countryCode = event.getCountryCode();
        String locale = resolveLocale(countryCode);

        String body =
                templateResolver.resolveTemplate(
                        "ride.completed.rider",
                        locale,
                        Map.of("amount", event.getFinalFare().toPlainString(), "currency", "TSh"));

        notificationService.sendPush(
                countryCode,
                event.getRiderId(),
                "Trip Completed",
                body,
                Map.of("type", "RIDE_COMPLETED", "rideId", event.getRideId().toString()),
                "ride.completed.rider");
    }

    private String resolveLocale(String countryCode) {
        if ("TZ".equals(countryCode)) return "sw-TZ";
        return "en";
    }
}
