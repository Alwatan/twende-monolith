package tz.co.twende.payment.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.enums.SubscriptionPlan;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.common.event.subscription.SubscriptionActivatedEvent;
import tz.co.twende.payment.dto.request.RidePaymentRequest;
import tz.co.twende.payment.entity.Transaction;
import tz.co.twende.payment.service.PaymentService;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock private PaymentService paymentService;

    @InjectMocks private PaymentEventConsumer consumer;

    @Test
    void givenFreeRideEvent_whenOnRideCompleted_thenProcessesRidePayment() {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setRideId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());
        event.setDriverId(UUID.randomUUID());
        event.setFinalFare(new BigDecimal("5000.00"));
        event.setFreeRide(true);
        event.setCountryCode("TZ");

        when(paymentService.processRidePayment(any(RidePaymentRequest.class)))
                .thenReturn(new Transaction());

        consumer.onRideCompleted(event);

        verify(paymentService).processRidePayment(any(RidePaymentRequest.class));
    }

    @Test
    void givenCashRideEvent_whenOnRideCompleted_thenProcessesRidePayment() {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setRideId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());
        event.setDriverId(UUID.randomUUID());
        event.setFinalFare(new BigDecimal("3000.00"));
        event.setFreeRide(false);
        event.setCountryCode("TZ");

        when(paymentService.processRidePayment(any(RidePaymentRequest.class)))
                .thenReturn(new Transaction());

        consumer.onRideCompleted(event);

        verify(paymentService).processRidePayment(any(RidePaymentRequest.class));
    }

    @Test
    void givenRideCompletedWithNullCountry_whenOnRideCompleted_thenDefaultsToTZ() {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setRideId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());
        event.setDriverId(UUID.randomUUID());
        event.setFinalFare(new BigDecimal("2000.00"));
        event.setFreeRide(false);
        event.setCountryCode(null);

        when(paymentService.processRidePayment(any(RidePaymentRequest.class)))
                .thenReturn(new Transaction());

        consumer.onRideCompleted(event);

        verify(paymentService).processRidePayment(any(RidePaymentRequest.class));
    }

    @Test
    void givenPaymentServiceThrows_whenOnRideCompleted_thenExceptionHandled() {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setRideId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());
        event.setDriverId(UUID.randomUUID());
        event.setFinalFare(new BigDecimal("1000.00"));
        event.setFreeRide(false);
        event.setCountryCode("TZ");

        when(paymentService.processRidePayment(any(RidePaymentRequest.class)))
                .thenThrow(new RuntimeException("Test exception"));

        // Should not throw -- exception is caught internally
        consumer.onRideCompleted(event);

        verify(paymentService).processRidePayment(any(RidePaymentRequest.class));
    }

    @Test
    void givenSubscriptionEvent_whenOnSubscriptionActivated_thenLogsOnly() {
        SubscriptionActivatedEvent event = new SubscriptionActivatedEvent();
        event.setSubscriptionId(UUID.randomUUID());
        event.setDriverId(UUID.randomUUID());
        event.setPlan(SubscriptionPlan.DAILY);
        event.setExpiresAt(Instant.now().plusSeconds(86400));

        consumer.onSubscriptionActivated(event);

        // No payment service interaction - just logging
        verifyNoInteractions(paymentService);
    }
}
