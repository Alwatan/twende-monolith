package tz.co.twende.payment.kafka;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tz.co.twende.common.enums.PaymentStatus;
import tz.co.twende.common.event.payment.FlatFeeDeductedEvent;
import tz.co.twende.common.event.payment.PaymentCompletedEvent;
import tz.co.twende.common.event.payment.PaymentInitiatedEvent;
import tz.co.twende.payment.config.KafkaConfig;
import tz.co.twende.payment.entity.Transaction;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPaymentCompleted(Transaction tx) {
        PaymentCompletedEvent event = new PaymentCompletedEvent();
        event.setTransactionId(tx.getId());
        event.setUserId(tx.getPayerId());
        event.setAmount(tx.getAmount());
        event.setStatus(PaymentStatus.COMPLETED);
        event.setCountryCode(tx.getCountryCode());
        event.setEventType("PAYMENT_COMPLETED");

        String key = tx.getCountryCode() + ":" + tx.getId();
        kafkaTemplate.send(KafkaConfig.TOPIC_PAYMENTS_COMPLETED, key, event);
        log.info("Published PaymentCompletedEvent: txId={}", tx.getId());
    }

    public void publishPaymentFailed(Transaction tx) {
        PaymentCompletedEvent event = new PaymentCompletedEvent();
        event.setTransactionId(tx.getId());
        event.setUserId(tx.getPayerId());
        event.setAmount(tx.getAmount());
        event.setStatus(PaymentStatus.FAILED);
        event.setCountryCode(tx.getCountryCode());
        event.setEventType("PAYMENT_FAILED");

        String key = tx.getCountryCode() + ":" + tx.getId();
        kafkaTemplate.send(KafkaConfig.TOPIC_PAYMENTS_FAILED, key, event);
        log.info("Published PaymentFailedEvent: txId={}", tx.getId());
    }

    public void publishFlatFeeDeducted(
            UUID driverId,
            UUID rideId,
            BigDecimal fareAmount,
            BigDecimal feePercentage,
            BigDecimal feeAmount,
            String countryCode,
            String currencyCode) {
        FlatFeeDeductedEvent event = new FlatFeeDeductedEvent();
        event.setDriverId(driverId);
        event.setRideId(rideId);
        event.setFareAmount(fareAmount);
        event.setFeePercentage(feePercentage);
        event.setFeeAmount(feeAmount);
        event.setCurrencyCode(currencyCode);
        event.setCountryCode(countryCode);
        event.setEventType("FLAT_FEE_DEDUCTED");

        String key = countryCode + ":" + driverId;
        kafkaTemplate.send(KafkaConfig.TOPIC_PAYMENTS_COMPLETED, key, event);
        log.info(
                "Published FlatFeeDeductedEvent: driverId={}, rideId={}, feeAmount={}",
                driverId,
                rideId,
                feeAmount);
    }

    public void publishPaymentInitiated(Transaction tx) {
        PaymentInitiatedEvent event = new PaymentInitiatedEvent();
        event.setTransactionId(tx.getId());
        event.setUserId(tx.getPayerId());
        event.setAmount(tx.getAmount());
        event.setCurrencyCode(tx.getCurrencyCode());
        event.setPaymentMethod(tx.getPaymentMethod().name());
        event.setCountryCode(tx.getCountryCode());
        event.setEventType("PAYMENT_INITIATED");

        String key = tx.getCountryCode() + ":" + tx.getId();
        kafkaTemplate.send(KafkaConfig.TOPIC_PAYMENTS_COMPLETED, key, event);
        log.info("Published PaymentInitiatedEvent: txId={}", tx.getId());
    }
}
