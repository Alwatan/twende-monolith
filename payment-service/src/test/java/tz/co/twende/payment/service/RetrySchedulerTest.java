package tz.co.twende.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.enums.PaymentMethod;
import tz.co.twende.common.enums.PaymentStatus;
import tz.co.twende.payment.entity.Transaction;
import tz.co.twende.payment.kafka.PaymentEventPublisher;
import tz.co.twende.payment.provider.PaymentGateway;
import tz.co.twende.payment.provider.PaymentResult;
import tz.co.twende.payment.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class RetrySchedulerTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private PaymentEventPublisher eventPublisher;

    @InjectMocks private RetryScheduler retryScheduler;

    @Test
    void givenNoProcessingTransactions_whenRetry_thenDoNothing() {
        when(transactionRepository.findByStatusAndInitiatedAtBefore(
                        eq(PaymentStatus.PROCESSING), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        retryScheduler.retryProcessingTransactions();

        verify(paymentGateway, never()).charge(any(), any());
    }

    @Test
    void givenProcessingTransaction_whenRetrySucceeds_thenMarkCompleted() {
        Transaction tx = createProcessingTransaction("selcom");
        when(transactionRepository.findByStatusAndInitiatedAtBefore(
                        eq(PaymentStatus.PROCESSING), any(Instant.class)))
                .thenReturn(List.of(tx));
        when(paymentGateway.hasProvider("selcom")).thenReturn(true);
        when(paymentGateway.charge(eq("selcom"), any()))
                .thenReturn(PaymentResult.success("RETRY-REF"));

        retryScheduler.retryProcessingTransactions();

        assertEquals(PaymentStatus.COMPLETED, tx.getStatus());
        assertEquals("RETRY-REF", tx.getProviderRef());
        verify(eventPublisher).publishPaymentCompleted(tx);
    }

    @Test
    void givenUnknownProvider_whenRetry_thenMarkFailed() {
        Transaction tx = createProcessingTransaction("unknown");
        when(transactionRepository.findByStatusAndInitiatedAtBefore(
                        eq(PaymentStatus.PROCESSING), any(Instant.class)))
                .thenReturn(List.of(tx));
        when(paymentGateway.hasProvider("unknown")).thenReturn(false);

        retryScheduler.retryProcessingTransactions();

        assertEquals(PaymentStatus.FAILED, tx.getStatus());
        verify(eventPublisher).publishPaymentFailed(tx);
    }

    private Transaction createProcessingTransaction(String provider) {
        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID());
        tx.setCountryCode("TZ");
        tx.setProvider(provider);
        tx.setStatus(PaymentStatus.PROCESSING);
        tx.setAmount(new BigDecimal("2000.00"));
        tx.setCurrencyCode("TZS");
        tx.setPayerId(UUID.randomUUID());
        tx.setPaymentMethod(PaymentMethod.MOBILE_MONEY);
        tx.setPaymentType("SUBSCRIPTION");
        tx.setReferenceId(UUID.randomUUID());
        tx.setReferenceType("SUBSCRIPTION");
        tx.setInitiatedAt(Instant.now().minusSeconds(600));
        return tx;
    }
}
