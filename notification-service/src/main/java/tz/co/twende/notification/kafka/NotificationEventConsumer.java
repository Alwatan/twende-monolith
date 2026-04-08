package tz.co.twende.notification.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.driver.DriverOfferNotificationEvent;
import tz.co.twende.common.event.loyalty.FreeRideOfferEarnedEvent;
import tz.co.twende.common.event.notification.SendNotificationEvent;
import tz.co.twende.common.event.payment.PaymentCompletedEvent;
import tz.co.twende.common.event.ride.BookingCompletedEvent;
import tz.co.twende.common.event.ride.BookingRequestedEvent;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.common.event.ride.RideStatusUpdatedEvent;
import tz.co.twende.common.event.subscription.SubscriptionExpiredEvent;
import tz.co.twende.notification.config.KafkaConfig;
import tz.co.twende.notification.kafka.handler.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final RideStatusNotificationHandler rideStatusHandler;
    private final RideCompletedNotificationHandler rideCompletedHandler;
    private final DriverOfferNotificationHandler driverOfferHandler;
    private final PaymentNotificationHandler paymentHandler;
    private final SubscriptionNotificationHandler subscriptionHandler;
    private final LoyaltyNotificationHandler loyaltyHandler;
    private final DirectNotificationHandler directHandler;
    private final BookingNotificationHandler bookingHandler;

    @KafkaListener(
            topics = KafkaConfig.TOPIC_RIDES_STATUS_UPDATED,
            groupId = "notification-service-group")
    @Async("notificationExecutor")
    public void onRideStatusUpdated(RideStatusUpdatedEvent event) {
        try {
            log.debug("Received ride status update: {}", event.getEventId());
            rideStatusHandler.handle(event);
        } catch (Exception e) {
            log.error("Error processing ride status update: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(
            topics = KafkaConfig.TOPIC_RIDES_COMPLETED,
            groupId = "notification-service-group")
    @Async("notificationExecutor")
    public void onRideCompleted(RideCompletedEvent event) {
        try {
            log.debug("Received ride completed: {}", event.getEventId());
            rideCompletedHandler.handle(event);
        } catch (Exception e) {
            log.error("Error processing ride completed: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(
            topics = KafkaConfig.TOPIC_DRIVERS_OFFER_NOTIFICATION,
            groupId = "notification-service-group")
    @Async("notificationExecutor")
    public void onDriverOfferNotification(DriverOfferNotificationEvent event) {
        try {
            log.debug("Received driver offer notification: {}", event.getEventId());
            driverOfferHandler.handle(event);
        } catch (Exception e) {
            log.error("Error processing driver offer: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(
            topics = KafkaConfig.TOPIC_PAYMENTS_COMPLETED,
            groupId = "notification-service-group")
    @Async("notificationExecutor")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        try {
            log.debug("Received payment completed: {}", event.getEventId());
            paymentHandler.handle(event);
        } catch (Exception e) {
            log.error("Error processing payment completed: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(
            topics = KafkaConfig.TOPIC_SUBSCRIPTIONS_EXPIRED,
            groupId = "notification-service-group")
    @Async("notificationExecutor")
    public void onSubscriptionExpired(SubscriptionExpiredEvent event) {
        try {
            log.debug("Received subscription expired: {}", event.getEventId());
            subscriptionHandler.handle(event);
        } catch (Exception e) {
            log.error("Error processing subscription expired: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(
            topics = KafkaConfig.TOPIC_LOYALTY_FREE_RIDE_EARNED,
            groupId = "notification-service-group")
    @Async("notificationExecutor")
    public void onFreeRideOfferEarned(FreeRideOfferEarnedEvent event) {
        try {
            log.debug("Received free ride offer earned: {}", event.getEventId());
            loyaltyHandler.handle(event);
        } catch (Exception e) {
            log.error("Error processing free ride offer: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(
            topics = KafkaConfig.TOPIC_NOTIFICATIONS_SEND,
            groupId = "notification-service-group")
    @Async("notificationExecutor")
    public void onSendNotification(SendNotificationEvent event) {
        try {
            log.debug("Received direct notification: {}", event.getEventId());
            directHandler.handle(event);
        } catch (Exception e) {
            log.error("Error processing direct notification: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(
            topics = KafkaConfig.TOPIC_BOOKING_REQUESTED,
            groupId = "notification-service-group")
    @Async("notificationExecutor")
    public void onBookingRequested(BookingRequestedEvent event) {
        try {
            log.debug("Received booking requested: {}", event.getEventId());
            bookingHandler.handleBookingRequested(event);
        } catch (Exception e) {
            log.error("Error processing booking requested: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(
            topics = KafkaConfig.TOPIC_BOOKING_COMPLETED,
            groupId = "notification-service-group")
    @Async("notificationExecutor")
    public void onBookingCompleted(BookingCompletedEvent event) {
        try {
            log.debug("Received booking completed: {}", event.getEventId());
            bookingHandler.handleBookingCompleted(event);
        } catch (Exception e) {
            log.error("Error processing booking completed: {}", e.getMessage(), e);
        }
    }
}
