package tz.co.twende.compliance.kafka;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.event.driver.DriverStatusUpdatedEvent;
import tz.co.twende.common.event.payment.PaymentCompletedEvent;
import tz.co.twende.common.event.rating.RatingSubmittedEvent;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.common.event.ride.RideRequestedEvent;
import tz.co.twende.common.event.ride.RideStatusUpdatedEvent;
import tz.co.twende.common.event.subscription.SubscriptionActivatedEvent;
import tz.co.twende.common.event.subscription.SubscriptionExpiredEvent;
import tz.co.twende.common.event.user.UserRegisteredEvent;
import tz.co.twende.compliance.entity.TripReport;
import tz.co.twende.compliance.service.AuditService;
import tz.co.twende.compliance.service.ComplianceService;

@ExtendWith(MockitoExtension.class)
class ComplianceEventConsumerTest {

    @Mock private ComplianceService complianceService;
    @Mock private AuditService auditService;

    @InjectMocks private ComplianceEventConsumer consumer;

    @Test
    void givenRideCompletedEvent_whenConsumed_thenCreatesReportAndAuditLog() {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setCountryCode("TZ");
        event.setRideId(UUID.randomUUID());
        event.setDriverId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());
        event.setVehicleType("BAJAJ");
        event.setFinalFare(new BigDecimal("3500"));

        when(complianceService.createTripReport(event)).thenReturn(new TripReport());

        consumer.onRideCompleted(event);

        verify(complianceService).createTripReport(event);
        verify(auditService)
                .log(
                        eq("TZ"),
                        eq("RIDE_COMPLETED"),
                        eq(event.getRideId()),
                        eq(event.getDriverId()),
                        eq(event));
    }

    @Test
    void givenRideRequestedEvent_whenConsumed_thenWritesAuditLogOnly() {
        RideRequestedEvent event = new RideRequestedEvent();
        event.setCountryCode("TZ");
        event.setRideId(UUID.randomUUID());
        event.setRiderId(UUID.randomUUID());

        consumer.onRideRequested(event);

        verify(auditService)
                .log(
                        eq("TZ"),
                        eq("RIDE_REQUESTED"),
                        eq(event.getRideId()),
                        eq(event.getRiderId()),
                        eq(event));
        verifyNoInteractions(complianceService);
    }

    @Test
    void givenRideCancelledEvent_whenConsumed_thenWritesAuditLog() {
        RideStatusUpdatedEvent event = new RideStatusUpdatedEvent();
        event.setCountryCode("TZ");
        event.setRideId(UUID.randomUUID());

        consumer.onRideCancelled(event);

        verify(auditService)
                .log(eq("TZ"), eq("RIDE_CANCELLED"), eq(event.getRideId()), isNull(), eq(event));
    }

    @Test
    void givenUserRegisteredEvent_whenConsumed_thenWritesAuditLog() {
        UserRegisteredEvent event = new UserRegisteredEvent();
        event.setCountryCode("TZ");
        event.setUserId(UUID.randomUUID());

        consumer.onUserRegistered(event);

        verify(auditService)
                .log(
                        eq("TZ"),
                        eq("USER_REGISTERED"),
                        eq(event.getUserId()),
                        eq(event.getUserId()),
                        eq(event));
    }

    @Test
    void givenPaymentCompletedEvent_whenConsumed_thenWritesAuditLog() {
        PaymentCompletedEvent event = new PaymentCompletedEvent();
        event.setCountryCode("TZ");
        event.setTransactionId(UUID.randomUUID());
        event.setUserId(UUID.randomUUID());

        consumer.onPaymentCompleted(event);

        verify(auditService)
                .log(
                        eq("TZ"),
                        eq("PAYMENT_COMPLETED"),
                        eq(event.getTransactionId()),
                        eq(event.getUserId()),
                        eq(event));
    }

    @Test
    void givenSubscriptionActivatedEvent_whenConsumed_thenWritesAuditLog() {
        SubscriptionActivatedEvent event = new SubscriptionActivatedEvent();
        event.setCountryCode("TZ");
        event.setSubscriptionId(UUID.randomUUID());
        event.setDriverId(UUID.randomUUID());

        consumer.onSubscriptionActivated(event);

        verify(auditService)
                .log(
                        eq("TZ"),
                        eq("SUBSCRIPTION_ACTIVATED"),
                        eq(event.getSubscriptionId()),
                        eq(event.getDriverId()),
                        eq(event));
    }

    @Test
    void givenRatingSubmittedEvent_whenConsumed_thenWritesAuditLog() {
        RatingSubmittedEvent event = new RatingSubmittedEvent();
        event.setCountryCode("TZ");
        event.setRideId(UUID.randomUUID());
        event.setRaterUserId(UUID.randomUUID());

        consumer.onRatingSubmitted(event);

        verify(auditService)
                .log(
                        eq("TZ"),
                        eq("RATING_SUBMITTED"),
                        eq(event.getRideId()),
                        eq(event.getRaterUserId()),
                        eq(event));
    }

    @Test
    void givenPaymentFailedEvent_whenConsumed_thenWritesAuditLog() {
        PaymentCompletedEvent event = new PaymentCompletedEvent();
        event.setCountryCode("TZ");
        event.setTransactionId(UUID.randomUUID());
        event.setUserId(UUID.randomUUID());

        consumer.onPaymentFailed(event);

        verify(auditService)
                .log(
                        eq("TZ"),
                        eq("PAYMENT_FAILED"),
                        eq(event.getTransactionId()),
                        eq(event.getUserId()),
                        eq(event));
    }

    @Test
    void givenSubscriptionExpiredEvent_whenConsumed_thenWritesAuditLog() {
        SubscriptionExpiredEvent event = new SubscriptionExpiredEvent();
        event.setCountryCode("TZ");
        event.setSubscriptionId(UUID.randomUUID());
        event.setDriverId(UUID.randomUUID());

        consumer.onSubscriptionExpired(event);

        verify(auditService)
                .log(
                        eq("TZ"),
                        eq("SUBSCRIPTION_EXPIRED"),
                        eq(event.getSubscriptionId()),
                        eq(event.getDriverId()),
                        eq(event));
    }

    @Test
    void givenDriverApprovedEvent_whenConsumed_thenWritesAuditLog() {
        DriverStatusUpdatedEvent event = new DriverStatusUpdatedEvent();
        event.setCountryCode("TZ");
        event.setDriverId(UUID.randomUUID());

        consumer.onDriverApproved(event);

        verify(auditService)
                .log(
                        eq("TZ"),
                        eq("DRIVER_APPROVED"),
                        eq(event.getDriverId()),
                        eq(event.getDriverId()),
                        eq(event));
    }

    @Test
    void givenDriverStatusUpdatedEvent_whenConsumed_thenWritesAuditLog() {
        DriverStatusUpdatedEvent event = new DriverStatusUpdatedEvent();
        event.setCountryCode("TZ");
        event.setDriverId(UUID.randomUUID());

        consumer.onDriverStatusUpdated(event);

        verify(auditService)
                .log(
                        eq("TZ"),
                        eq("DRIVER_STATUS_UPDATED"),
                        eq(event.getDriverId()),
                        eq(event.getDriverId()),
                        eq(event));
    }
}
