package tz.co.twende.payment.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.enums.PaymentMethod;
import tz.co.twende.common.enums.PaymentStatus;
import tz.co.twende.common.exception.ConflictException;
import tz.co.twende.payment.entity.CashDeclaration;
import tz.co.twende.payment.entity.Transaction;
import tz.co.twende.payment.kafka.PaymentEventPublisher;
import tz.co.twende.payment.repository.CashDeclarationRepository;
import tz.co.twende.payment.repository.TransactionRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashDeclarationService {

    private final CashDeclarationRepository cashDeclarationRepository;
    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final PaymentEventPublisher eventPublisher;

    @Transactional
    public CashDeclaration declareCash(
            UUID rideId, UUID driverId, BigDecimal amount, String countryCode, String currency) {
        if (cashDeclarationRepository.existsByRideId(rideId)) {
            throw new ConflictException("Cash already declared for ride: " + rideId);
        }

        CashDeclaration declaration = new CashDeclaration();
        declaration.setRideId(rideId);
        declaration.setDriverId(driverId);
        declaration.setAmount(amount);
        declaration.setDeclaredAt(Instant.now());
        declaration.setVerified(false);
        cashDeclarationRepository.save(declaration);

        // Credit driver wallet with declared cash amount
        walletService.creditDriverWallet(
                driverId, amount, rideId, "Cash declaration for ride", countryCode, currency);

        // Create transaction record if not already present
        if (!transactionRepository.existsByReferenceIdAndReferenceType(rideId, "RIDE")) {
            Transaction tx = new Transaction();
            tx.setCountryCode(countryCode);
            tx.setReferenceId(rideId);
            tx.setReferenceType("RIDE");
            tx.setPayerId(driverId);
            tx.setPayeeId(driverId);
            tx.setPaymentType("RIDE");
            tx.setPaymentMethod(PaymentMethod.CASH);
            tx.setProvider("cash");
            tx.setAmount(amount);
            tx.setCurrencyCode(currency);
            tx.setStatus(PaymentStatus.COMPLETED);
            tx.setProviderRef("CASH-DECLARE-" + rideId);
            tx.setInitiatedAt(Instant.now());
            tx.setCompletedAt(Instant.now());
            transactionRepository.save(tx);

            eventPublisher.publishPaymentCompleted(tx);
        }

        log.info("Cash declared: rideId={}, driverId={}, amount={}", rideId, driverId, amount);

        return declaration;
    }
}
