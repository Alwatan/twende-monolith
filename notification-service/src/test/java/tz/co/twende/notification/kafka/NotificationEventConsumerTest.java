package tz.co.twende.notification.kafka;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.enums.NotificationType;
import tz.co.twende.common.enums.PaymentStatus;
import tz.co.twende.common.enums.RideStatus;
import tz.co.twende.common.enums.VehicleType;
import tz.co.twende.common.event.driver.DriverOfferNotificationEvent;
import tz.co.twende.common.event.loyalty.FreeRideOfferEarnedEvent;
import tz.co.twende.common.event.notification.SendNotificationEvent;
import tz.co.twende.common.event.payment.PaymentCompletedEvent;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.common.event.ride.RideStatusUpdatedEvent;
import tz.co.twende.common.event.subscription.SubscriptionExpiredEvent;
import tz.co.twende.notification.kafka.handler.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock private RideStatusNotificationHandler rideStatusHandler;
    @Mock private RideCompletedNotificationHandler rideCompletedHandler;
    @Mock private DriverOfferNotificationHandler driverOfferHandler;
    @Mock private PaymentNotificationHandler paymentHandler;
    @Mock private SubscriptionNotificationHandler subscriptionHandler;
    @Mock private LoyaltyNotificationHandler loyaltyHandler;
    @Mock private DirectNotificationHandler directHandler;

    @InjectMocks private NotificationEventConsumer consumer;

    @Test
    void givenRideStatusEvent_whenConsumed_thenDelegatedToHandler() {
        RideStatusUpdatedEvent event = new RideStatusUpdatedEvent();
        event.setRideId(UUID.randomUUID());
        event.setNewStatus(RideStatus.DRIVER_ASSIGNED);
        event.setCountryCode("TZ");

        consumer.onRideStatusUpdated(event);

        verify(rideStatusHandler).handle(event);
    }

    @Test
    void givenRideCompletedEvent_whenConsumed_thenDelegatedToHandler() {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setRideId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());
        event.setFinalFare(new BigDecimal("5000"));
        event.setCountryCode("TZ");

        consumer.onRideCompleted(event);

        verify(rideCompletedHandler).handle(event);
    }

    @Test
    void givenDriverOfferEvent_whenConsumed_thenDelegatedToHandler() {
        DriverOfferNotificationEvent event = new DriverOfferNotificationEvent();
        event.setRideId(UUID.randomUUID());
        event.setDriverId(UUID.randomUUID());
        event.setTotalFare(new BigDecimal("5000"));
        event.setCountryCode("TZ");

        consumer.onDriverOfferNotification(event);

        verify(driverOfferHandler).handle(event);
    }

    @Test
    void givenPaymentCompletedEvent_whenConsumed_thenDelegatedToHandler() {
        PaymentCompletedEvent event = new PaymentCompletedEvent();
        event.setTransactionId(UUID.randomUUID());
        event.setUserId(UUID.randomUUID());
        event.setAmount(new BigDecimal("2000"));
        event.setStatus(PaymentStatus.COMPLETED);
        event.setCountryCode("TZ");

        consumer.onPaymentCompleted(event);

        verify(paymentHandler).handle(event);
    }

    @Test
    void givenSubscriptionExpiredEvent_whenConsumed_thenDelegatedToHandler() {
        SubscriptionExpiredEvent event = new SubscriptionExpiredEvent();
        event.setSubscriptionId(UUID.randomUUID());
        event.setDriverId(UUID.randomUUID());
        event.setCountryCode("TZ");

        consumer.onSubscriptionExpired(event);

        verify(subscriptionHandler).handle(event);
    }

    @Test
    void givenFreeRideOfferEvent_whenConsumed_thenDelegatedToHandler() {
        FreeRideOfferEarnedEvent event = new FreeRideOfferEarnedEvent();
        event.setOfferId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());
        event.setVehicleType(VehicleType.BAJAJ);
        event.setMaxDistanceKm(new BigDecimal("10"));
        event.setExpiresAt(Instant.now());
        event.setCountryCode("TZ");

        consumer.onFreeRideOfferEarned(event);

        verify(loyaltyHandler).handle(event);
    }

    @Test
    void givenDirectNotificationEvent_whenConsumed_thenDelegatedToHandler() {
        SendNotificationEvent event = new SendNotificationEvent();
        event.setRecipientUserId(UUID.randomUUID());
        event.setType(NotificationType.PUSH);
        event.setBodyKey("test.body");
        event.setCountryCode("TZ");

        consumer.onSendNotification(event);

        verify(directHandler).handle(event);
    }

    @Test
    void givenHandlerThrows_whenConsumed_thenExceptionHandledGracefully() {
        RideStatusUpdatedEvent event = new RideStatusUpdatedEvent();
        event.setRideId(UUID.randomUUID());
        event.setNewStatus(RideStatus.DRIVER_ASSIGNED);
        event.setCountryCode("TZ");

        doThrow(new RuntimeException("Handler failed")).when(rideStatusHandler).handle(any());

        // Should not throw
        consumer.onRideStatusUpdated(event);

        verify(rideStatusHandler).handle(event);
    }
}
