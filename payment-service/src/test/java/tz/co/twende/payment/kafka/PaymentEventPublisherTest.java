package tz.co.twende.payment.kafka;

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
import org.springframework.kafka.core.KafkaTemplate;
import tz.co.twende.common.enums.PaymentMethod;
import tz.co.twende.common.enums.PaymentStatus;
import tz.co.twende.payment.config.KafkaConfig;
import tz.co.twende.payment.entity.Transaction;

@ExtendWith(MockitoExtension.class)
class PaymentEventPublisherTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private PaymentEventPublisher publisher;

    @Test
    void givenCompletedTransaction_whenPublishCompleted_thenSendsToCorrectTopic() {
        Transaction tx = createTransaction();

        publisher.publishPaymentCompleted(tx);

        verify(kafkaTemplate).send(eq(KafkaConfig.TOPIC_PAYMENTS_COMPLETED), anyString(), any());
    }

    @Test
    void givenFailedTransaction_whenPublishFailed_thenSendsToCorrectTopic() {
        Transaction tx = createTransaction();

        publisher.publishPaymentFailed(tx);

        verify(kafkaTemplate).send(eq(KafkaConfig.TOPIC_PAYMENTS_FAILED), anyString(), any());
    }

    @Test
    void givenInitiatedTransaction_whenPublishInitiated_thenSendsEvent() {
        Transaction tx = createTransaction();

        publisher.publishPaymentInitiated(tx);

        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void givenFlatFeeDeduction_whenPublishFlatFeeDeducted_thenSendsEvent() {
        UUID driverId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        publisher.publishFlatFeeDeducted(
                driverId,
                rideId,
                new BigDecimal("10000.00"),
                new BigDecimal("15.00"),
                new BigDecimal("1500.00"),
                "TZ",
                "TZS");

        verify(kafkaTemplate).send(eq(KafkaConfig.TOPIC_PAYMENTS_COMPLETED), anyString(), any());
    }

    private Transaction createTransaction() {
        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID());
        tx.setCountryCode("TZ");
        tx.setPayerId(UUID.randomUUID());
        tx.setAmount(new BigDecimal("2000.00"));
        tx.setCurrencyCode("TZS");
        tx.setStatus(PaymentStatus.COMPLETED);
        tx.setPaymentMethod(PaymentMethod.CASH);
        tx.setInitiatedAt(Instant.now());
        return tx;
    }
}
