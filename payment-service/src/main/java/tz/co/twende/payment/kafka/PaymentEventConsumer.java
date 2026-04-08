package tz.co.twende.payment.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.common.event.subscription.SubscriptionActivatedEvent;
import tz.co.twende.payment.config.KafkaConfig;
import tz.co.twende.payment.dto.request.RidePaymentRequest;
import tz.co.twende.payment.service.PaymentService;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final PaymentService paymentService;

    @KafkaListener(
            topics = KafkaConfig.TOPIC_RIDES_COMPLETED,
            groupId = "${spring.kafka.consumer.group-id}")
    public void onRideCompleted(RideCompletedEvent event) {
        log.info(
                "Received RideCompletedEvent: rideId={}, freeRide={}",
                event.getRideId(),
                event.isFreeRide());

        try {
            RidePaymentRequest request = new RidePaymentRequest();
            request.setRideId(event.getRideId());
            request.setRiderId(event.getRiderId());
            request.setDriverId(event.getDriverId());
            request.setAmount(event.getFinalFare());
            request.setCurrencyCode("TZS");
            request.setCountryCode(event.getCountryCode() != null ? event.getCountryCode() : "TZ");
            request.setFreeRide(event.isFreeRide());

            paymentService.processRidePayment(request);
        } catch (Exception e) {
            log.error(
                    "Failed to process ride payment for rideId={}: {}",
                    event.getRideId(),
                    e.getMessage(),
                    e);
        }
    }

    @KafkaListener(
            topics = KafkaConfig.TOPIC_SUBSCRIPTIONS_ACTIVATED,
            groupId = "${spring.kafka.consumer.group-id}")
    public void onSubscriptionActivated(SubscriptionActivatedEvent event) {
        log.info(
                "Received SubscriptionActivatedEvent: subscriptionId={}, driverId={}",
                event.getSubscriptionId(),
                event.getDriverId());

        // Subscription payment is handled via internal REST API call from subscription-service.
        // This listener records the activation for reference only.
        log.info("Subscription activation recorded: subscriptionId={}", event.getSubscriptionId());
    }
}
