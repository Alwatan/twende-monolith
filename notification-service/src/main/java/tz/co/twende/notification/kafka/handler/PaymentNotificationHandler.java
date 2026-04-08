package tz.co.twende.notification.kafka.handler;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.payment.PaymentCompletedEvent;
import tz.co.twende.notification.service.NotificationService;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentNotificationHandler {

    private final NotificationService notificationService;

    public void handle(PaymentCompletedEvent event) {
        log.debug("Handling payment completed: {}", event.getTransactionId());

        String countryCode = event.getCountryCode();

        notificationService.sendPush(
                countryCode,
                event.getUserId(),
                "Payment Confirmed",
                "Payment of " + event.getAmount().toPlainString() + " has been processed.",
                Map.of(
                        "type", "PAYMENT_COMPLETED",
                        "transactionId", event.getTransactionId().toString(),
                        "amount", event.getAmount().toPlainString()),
                "payment.completed");
    }
}
