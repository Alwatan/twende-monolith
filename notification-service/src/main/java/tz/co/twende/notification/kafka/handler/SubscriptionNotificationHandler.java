package tz.co.twende.notification.kafka.handler;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.subscription.SubscriptionExpiredEvent;
import tz.co.twende.notification.service.NotificationService;
import tz.co.twende.notification.service.TemplateResolver;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionNotificationHandler {

    private final NotificationService notificationService;
    private final TemplateResolver templateResolver;

    public void handle(SubscriptionExpiredEvent event) {
        log.debug("Handling subscription expired: {}", event.getSubscriptionId());

        String countryCode = event.getCountryCode();
        String locale = resolveLocale(countryCode);

        String body =
                templateResolver.resolveTemplate("subscription.expired.driver", locale, Map.of());

        notificationService.sendPush(
                countryCode,
                event.getDriverId(),
                "Bundle Expired",
                body,
                Map.of(
                        "type",
                        "SUBSCRIPTION_EXPIRED",
                        "subscriptionId",
                        event.getSubscriptionId().toString()),
                "subscription.expired.driver");
    }

    private String resolveLocale(String countryCode) {
        if ("TZ".equals(countryCode)) return "sw-TZ";
        return "en";
    }
}
