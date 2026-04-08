package tz.co.twende.notification.kafka.handler;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tz.co.twende.common.enums.NotificationType;
import tz.co.twende.common.event.notification.SendNotificationEvent;
import tz.co.twende.notification.service.NotificationService;
import tz.co.twende.notification.service.TemplateResolver;

@Slf4j
@Component
@RequiredArgsConstructor
public class DirectNotificationHandler {

    private final NotificationService notificationService;
    private final TemplateResolver templateResolver;

    public void handle(SendNotificationEvent event) {
        log.debug(
                "Handling direct notification: type={}, recipient={}",
                event.getType(),
                event.getRecipientUserId());

        String countryCode = event.getCountryCode();
        String locale = resolveLocale(countryCode);

        String title = null;
        if (event.getTitleKey() != null) {
            title =
                    templateResolver.resolveTemplate(
                            event.getTitleKey(), locale, event.getTemplateParams());
        }

        String body =
                templateResolver.resolveTemplate(
                        event.getBodyKey(), locale, event.getTemplateParams());

        if (event.getType() == NotificationType.PUSH) {
            notificationService.sendPush(
                    countryCode,
                    event.getRecipientUserId(),
                    title,
                    body,
                    event.getData() != null ? event.getData() : Map.of(),
                    event.getBodyKey());
        } else if (event.getType() == NotificationType.SMS) {
            notificationService.sendSms(
                    countryCode, event.getRecipientUserId(), null, body, event.getBodyKey());
        }
    }

    private String resolveLocale(String countryCode) {
        if ("TZ".equals(countryCode)) return "sw-TZ";
        return "en";
    }
}
