package tz.co.twende.compliance.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.driver.DriverStatusUpdatedEvent;
import tz.co.twende.common.event.payment.PaymentCompletedEvent;
import tz.co.twende.common.event.rating.RatingSubmittedEvent;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.common.event.ride.RideRequestedEvent;
import tz.co.twende.common.event.ride.RideStatusUpdatedEvent;
import tz.co.twende.common.event.subscription.SubscriptionActivatedEvent;
import tz.co.twende.common.event.subscription.SubscriptionExpiredEvent;
import tz.co.twende.common.event.user.UserRegisteredEvent;
import tz.co.twende.compliance.service.AuditService;
import tz.co.twende.compliance.service.ComplianceService;

@Component
@RequiredArgsConstructor
@Slf4j
public class ComplianceEventConsumer {

    private final ComplianceService complianceService;
    private final AuditService auditService;

    @KafkaListener(topics = "twende.rides.completed", groupId = "twende-compliance")
    public void onRideCompleted(RideCompletedEvent event) {
        log.debug("Received ride completed event for ride {}", event.getRideId());
        complianceService.createTripReport(event);
        auditService.log(
                event.getCountryCode(),
                "RIDE_COMPLETED",
                event.getRideId(),
                event.getDriverId(),
                event);
    }

    @KafkaListener(topics = "twende.rides.requested", groupId = "twende-compliance")
    public void onRideRequested(RideRequestedEvent event) {
        auditService.log(
                event.getCountryCode(),
                "RIDE_REQUESTED",
                event.getRideId(),
                event.getRiderId(),
                event);
    }

    @KafkaListener(topics = "twende.rides.cancelled", groupId = "twende-compliance")
    public void onRideCancelled(RideStatusUpdatedEvent event) {
        auditService.log(event.getCountryCode(), "RIDE_CANCELLED", event.getRideId(), null, event);
    }

    @KafkaListener(topics = "twende.payments.completed", groupId = "twende-compliance")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        auditService.log(
                event.getCountryCode(),
                "PAYMENT_COMPLETED",
                event.getTransactionId(),
                event.getUserId(),
                event);
    }

    @KafkaListener(topics = "twende.payments.failed", groupId = "twende-compliance")
    public void onPaymentFailed(PaymentCompletedEvent event) {
        auditService.log(
                event.getCountryCode(),
                "PAYMENT_FAILED",
                event.getTransactionId(),
                event.getUserId(),
                event);
    }

    @KafkaListener(topics = "twende.subscriptions.activated", groupId = "twende-compliance")
    public void onSubscriptionActivated(SubscriptionActivatedEvent event) {
        auditService.log(
                event.getCountryCode(),
                "SUBSCRIPTION_ACTIVATED",
                event.getSubscriptionId(),
                event.getDriverId(),
                event);
    }

    @KafkaListener(topics = "twende.subscriptions.expired", groupId = "twende-compliance")
    public void onSubscriptionExpired(SubscriptionExpiredEvent event) {
        auditService.log(
                event.getCountryCode(),
                "SUBSCRIPTION_EXPIRED",
                event.getSubscriptionId(),
                event.getDriverId(),
                event);
    }

    @KafkaListener(topics = "twende.users.registered", groupId = "twende-compliance")
    public void onUserRegistered(UserRegisteredEvent event) {
        auditService.log(
                event.getCountryCode(),
                "USER_REGISTERED",
                event.getUserId(),
                event.getUserId(),
                event);
    }

    @KafkaListener(topics = "twende.drivers.approved", groupId = "twende-compliance")
    public void onDriverApproved(DriverStatusUpdatedEvent event) {
        auditService.log(
                event.getCountryCode(),
                "DRIVER_APPROVED",
                event.getDriverId(),
                event.getDriverId(),
                event);
    }

    @KafkaListener(topics = "twende.drivers.status-updated", groupId = "twende-compliance")
    public void onDriverStatusUpdated(DriverStatusUpdatedEvent event) {
        auditService.log(
                event.getCountryCode(),
                "DRIVER_STATUS_UPDATED",
                event.getDriverId(),
                event.getDriverId(),
                event);
    }

    @KafkaListener(topics = "twende.ratings.submitted", groupId = "twende-compliance")
    public void onRatingSubmitted(RatingSubmittedEvent event) {
        auditService.log(
                event.getCountryCode(),
                "RATING_SUBMITTED",
                event.getRideId(),
                event.getRaterUserId(),
                event);
    }
}
