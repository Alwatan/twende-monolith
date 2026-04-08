package tz.co.twende.payment.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tz.co.twende.common.enums.PaymentStatus;
import tz.co.twende.payment.entity.Transaction;
import tz.co.twende.payment.kafka.PaymentEventPublisher;
import tz.co.twende.payment.provider.ChargeRequest;
import tz.co.twende.payment.provider.PaymentGateway;
import tz.co.twende.payment.provider.PaymentResult;
import tz.co.twende.payment.repository.TransactionRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class RetryScheduler {

    private final TransactionRepository transactionRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentEventPublisher eventPublisher;

    @Scheduled(fixedDelay = 300_000)
    public void retryProcessingTransactions() {
        Instant threshold = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<Transaction> processing =
                transactionRepository.findByStatusAndInitiatedAtBefore(
                        PaymentStatus.PROCESSING, threshold);

        if (processing.isEmpty()) {
            return;
        }

        log.info("Retrying {} stuck PROCESSING transactions", processing.size());

        for (Transaction tx : processing) {
            try {
                if (tx.getProvider() == null || !paymentGateway.hasProvider(tx.getProvider())) {
                    log.warn(
                            "Skipping retry for tx={}: unknown provider={}",
                            tx.getId(),
                            tx.getProvider());
                    tx.setStatus(PaymentStatus.FAILED);
                    tx.setFailureReason("Unknown provider: " + tx.getProvider());
                    transactionRepository.save(tx);
                    eventPublisher.publishPaymentFailed(tx);
                    continue;
                }

                ChargeRequest chargeRequest =
                        ChargeRequest.builder()
                                .transactionId(tx.getId())
                                .amount(tx.getAmount())
                                .currencyCode(tx.getCurrencyCode())
                                .description("Retry payment")
                                .build();

                PaymentResult result = paymentGateway.charge(tx.getProvider(), chargeRequest);

                if (result.isSuccess()) {
                    tx.setStatus(PaymentStatus.COMPLETED);
                    tx.setProviderRef(result.getReference());
                    tx.setCompletedAt(Instant.now());
                    transactionRepository.save(tx);
                    eventPublisher.publishPaymentCompleted(tx);
                    log.info("Retry succeeded for tx={}", tx.getId());
                } else {
                    tx.setStatus(PaymentStatus.FAILED);
                    tx.setFailureReason(result.getErrorMessage());
                    transactionRepository.save(tx);
                    eventPublisher.publishPaymentFailed(tx);
                    log.warn("Retry failed for tx={}: {}", tx.getId(), result.getErrorMessage());
                }
            } catch (Exception e) {
                log.error("Retry error for tx={}: {}", tx.getId(), e.getMessage(), e);
                tx.setStatus(PaymentStatus.FAILED);
                tx.setFailureReason(e.getMessage());
                transactionRepository.save(tx);
                eventPublisher.publishPaymentFailed(tx);
            }
        }
    }
}
