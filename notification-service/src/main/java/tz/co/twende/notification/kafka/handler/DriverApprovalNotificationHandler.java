package tz.co.twende.notification.kafka.handler;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tz.co.twende.notification.service.NotificationService;
import tz.co.twende.notification.service.TemplateResolver;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverApprovalNotificationHandler {

    private final NotificationService notificationService;
    private final TemplateResolver templateResolver;

    public void handle(UUID driverId, String countryCode) {
        log.debug("Handling driver approved: {}", driverId);

        String locale = resolveLocale(countryCode);

        String body = templateResolver.resolveTemplate("driver.approved", locale, Map.of());

        notificationService.sendPush(
                countryCode,
                driverId,
                "Account Approved",
                body,
                Map.of("type", "DRIVER_APPROVED"),
                "driver.approved");
    }

    private String resolveLocale(String countryCode) {
        if ("TZ".equals(countryCode)) return "sw-TZ";
        return "en";
    }
}
