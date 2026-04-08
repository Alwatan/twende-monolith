package tz.co.twende.notification.kafka;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.enums.PaymentStatus;
import tz.co.twende.common.event.payment.PaymentCompletedEvent;
import tz.co.twende.notification.kafka.handler.PaymentNotificationHandler;
import tz.co.twende.notification.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class PaymentNotificationHandlerTest {

    @Mock private NotificationService notificationService;

    @InjectMocks private PaymentNotificationHandler handler;

    @Test
    void givenPaymentCompletedEvent_whenProcessed_thenPushSentToUser() {
        PaymentCompletedEvent event = new PaymentCompletedEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setUserId(UUID.randomUUID());
        event.setAmount(new BigDecimal("2000"));
        event.setStatus(PaymentStatus.COMPLETED);
        event.setCountryCode("TZ");

        handler.handle(event);

        verify(notificationService)
                .sendPush(
                        eq("TZ"),
                        eq(event.getUserId()),
                        eq("Payment Confirmed"),
                        contains("2000"),
                        any(Map.class),
                        eq("payment.completed"));
    }
}
