package tz.co.twende.payment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.enums.PaymentMethod;
import tz.co.twende.common.enums.PaymentStatus;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ConflictException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.payment.client.ConfigClient;
import tz.co.twende.payment.client.SubscriptionServiceClient;
import tz.co.twende.payment.dto.request.RidePaymentRequest;
import tz.co.twende.payment.dto.request.SubscriptionPaymentRequest;
import tz.co.twende.payment.dto.request.WithdrawRequest;
import tz.co.twende.payment.dto.response.RevenueModelDto;
import tz.co.twende.payment.entity.Transaction;
import tz.co.twende.payment.kafka.PaymentEventPublisher;
import tz.co.twende.payment.provider.*;
import tz.co.twende.payment.repository.TransactionRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final PaymentGateway paymentGateway;
    private final PaymentEventPublisher eventPublisher;
    private final SubscriptionServiceClient subscriptionServiceClient;
    private final ConfigClient configClient;

    @Transactional
    public Transaction processRidePayment(RidePaymentRequest request) {
        if (transactionRepository.existsByReferenceIdAndReferenceType(
                request.getRideId(), "RIDE")) {
            throw new ConflictException("Payment already exists for ride: " + request.getRideId());
        }

        Transaction tx = new Transaction();
        tx.setCountryCode(request.getCountryCode());
        tx.setReferenceId(request.getRideId());
        tx.setReferenceType("RIDE");
        tx.setPayerId(request.getRiderId());
        tx.setPayeeId(request.getDriverId());
        tx.setPaymentType("RIDE");
        tx.setAmount(request.getAmount());
        tx.setCurrencyCode(request.getCurrencyCode());
        tx.setInitiatedAt(Instant.now());

        if (request.isFreeRide()) {
            // Twende pays the driver for free loyalty rides
            tx.setPaymentMethod(PaymentMethod.CASH);
            tx.setProvider("system");
            tx.setStatus(PaymentStatus.COMPLETED);
            tx.setCompletedAt(Instant.now());
            tx.setProviderRef("FREE-RIDE-" + request.getRideId());
            transactionRepository.save(tx);

            // Credit driver wallet with the full fare
            walletService.creditDriverWallet(
                    request.getDriverId(),
                    request.getAmount(),
                    request.getRideId(),
                    "Free ride earnings (Twende pays)",
                    request.getCountryCode(),
                    request.getCurrencyCode());

            eventPublisher.publishPaymentCompleted(tx);
        } else {
            // Cash ride -- driver already has cash, just record
            tx.setPaymentMethod(PaymentMethod.CASH);
            tx.setProvider("cash");
            tx.setStatus(PaymentStatus.COMPLETED);
            tx.setCompletedAt(Instant.now());
            tx.setProviderRef("CASH-" + request.getRideId());
            transactionRepository.save(tx);

            eventPublisher.publishPaymentCompleted(tx);

            // Check if driver is on flat fee and deduct Twende's cut
            deductFlatFeeIfApplicable(
                    request.getDriverId(),
                    request.getRideId(),
                    request.getAmount(),
                    request.getCountryCode(),
                    request.getCurrencyCode());
        }

        log.info(
                "Ride payment processed: rideId={}, freeRide={}, amount={}",
                request.getRideId(),
                request.isFreeRide(),
                request.getAmount());

        return tx;
    }

    void deductFlatFeeIfApplicable(
            UUID driverId,
            UUID rideId,
            BigDecimal fareAmount,
            String countryCode,
            String currencyCode) {
        try {
            RevenueModelDto revenueModel = subscriptionServiceClient.getRevenueModel(driverId);
            if (revenueModel == null || !"FLAT_FEE".equals(revenueModel.getRevenueModel())) {
                return;
            }

            String serviceCategory =
                    revenueModel.getServiceCategory() != null
                            ? revenueModel.getServiceCategory()
                            : "RIDE";
            BigDecimal percentage = configClient.getFlatFeePercentage(countryCode, serviceCategory);
            BigDecimal feeAmount =
                    fareAmount
                            .multiply(percentage)
                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            walletService.debitFlatFee(
                    driverId,
                    feeAmount,
                    rideId,
                    "Flat fee deduction (" + percentage + "%) for ride " + rideId);

            eventPublisher.publishFlatFeeDeducted(
                    driverId, rideId, fareAmount, percentage, feeAmount, countryCode, currencyCode);

            log.info(
                    "Flat fee deducted: driverId={}, rideId={}, fare={}, fee={}",
                    driverId,
                    rideId,
                    fareAmount,
                    feeAmount);
        } catch (Exception e) {
            log.error(
                    "Failed to deduct flat fee for driver={}, ride={}: {}",
                    driverId,
                    rideId,
                    e.getMessage(),
                    e);
        }
    }

    @Transactional
    public Transaction processSubscriptionPayment(SubscriptionPaymentRequest request) {
        if (transactionRepository.existsByReferenceIdAndReferenceType(
                request.getSubscriptionId(), "SUBSCRIPTION")) {
            throw new ConflictException(
                    "Payment already exists for subscription: " + request.getSubscriptionId());
        }

        Transaction tx = new Transaction();
        tx.setCountryCode(request.getCountryCode());
        tx.setReferenceId(request.getSubscriptionId());
        tx.setReferenceType("SUBSCRIPTION");
        tx.setPayerId(request.getDriverId());
        tx.setPaymentType("SUBSCRIPTION");
        tx.setPaymentMethod(PaymentMethod.MOBILE_MONEY);
        tx.setProvider("selcom");
        tx.setAmount(request.getAmount());
        tx.setCurrencyCode(request.getCurrencyCode());
        tx.setStatus(PaymentStatus.PROCESSING);
        tx.setInitiatedAt(Instant.now());
        transactionRepository.save(tx);

        eventPublisher.publishPaymentInitiated(tx);

        try {
            ChargeRequest chargeRequest =
                    ChargeRequest.builder()
                            .transactionId(tx.getId())
                            .mobileNumber(request.getMobileNumber())
                            .amount(request.getAmount())
                            .currencyCode(request.getCurrencyCode())
                            .description("Twende subscription payment")
                            .build();

            PaymentResult result = paymentGateway.charge("selcom", chargeRequest);

            if (result.isSuccess()) {
                tx.setStatus(PaymentStatus.COMPLETED);
                tx.setProviderRef(result.getReference());
                tx.setCompletedAt(Instant.now());
                transactionRepository.save(tx);
                eventPublisher.publishPaymentCompleted(tx);
            } else {
                tx.setStatus(PaymentStatus.FAILED);
                tx.setFailureReason(result.getErrorMessage());
                transactionRepository.save(tx);
                eventPublisher.publishPaymentFailed(tx);
            }
        } catch (Exception e) {
            log.error("Selcom charge failed for subscription: {}", request.getSubscriptionId(), e);
            tx.setStatus(PaymentStatus.FAILED);
            tx.setFailureReason(e.getMessage());
            transactionRepository.save(tx);
            eventPublisher.publishPaymentFailed(tx);
        }

        return tx;
    }

    @Transactional
    public Transaction processWithdrawal(
            UUID driverId, WithdrawRequest request, String countryCode) {
        Transaction tx = new Transaction();
        tx.setCountryCode(countryCode);
        tx.setReferenceId(driverId);
        tx.setReferenceType("WITHDRAWAL");
        tx.setPayerId(driverId);
        tx.setPaymentType("WITHDRAWAL");
        tx.setPaymentMethod(PaymentMethod.MOBILE_MONEY);
        tx.setProvider("selcom");
        tx.setAmount(request.getAmount());
        tx.setCurrencyCode("TZS");
        tx.setStatus(PaymentStatus.PROCESSING);
        tx.setInitiatedAt(Instant.now());
        transactionRepository.save(tx);

        // Debit wallet first
        walletService.debitDriverWallet(
                driverId, request.getAmount(), tx.getId(), "Wallet withdrawal");

        try {
            DisburseRequest disburseRequest =
                    DisburseRequest.builder()
                            .transactionId(tx.getId())
                            .mobileNumber(request.getMobileNumber())
                            .amount(request.getAmount())
                            .currencyCode("TZS")
                            .description("Twende wallet withdrawal")
                            .build();

            PaymentResult result = paymentGateway.disburse("selcom", disburseRequest);

            if (result.isSuccess()) {
                tx.setStatus(PaymentStatus.COMPLETED);
                tx.setProviderRef(result.getReference());
                tx.setCompletedAt(Instant.now());
                transactionRepository.save(tx);
                eventPublisher.publishPaymentCompleted(tx);
            } else {
                // Re-credit wallet on failure
                walletService.creditDriverWallet(
                        driverId,
                        request.getAmount(),
                        tx.getId(),
                        "Withdrawal reversal: " + result.getErrorMessage(),
                        countryCode,
                        "TZS");
                tx.setStatus(PaymentStatus.FAILED);
                tx.setFailureReason(result.getErrorMessage());
                transactionRepository.save(tx);
                eventPublisher.publishPaymentFailed(tx);
            }
        } catch (Exception e) {
            log.error("Withdrawal disburse failed for driver: {}", driverId, e);
            // Re-credit wallet on failure
            walletService.creditDriverWallet(
                    driverId,
                    request.getAmount(),
                    tx.getId(),
                    "Withdrawal reversal: " + e.getMessage(),
                    countryCode,
                    "TZS");
            tx.setStatus(PaymentStatus.FAILED);
            tx.setFailureReason(e.getMessage());
            transactionRepository.save(tx);
            eventPublisher.publishPaymentFailed(tx);
        }

        return tx;
    }

    @Transactional
    public Transaction processRefund(UUID transactionId, String reason) {
        Transaction original =
                transactionRepository
                        .findById(transactionId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Transaction not found: " + transactionId));

        if (original.getStatus() != PaymentStatus.COMPLETED) {
            throw new BadRequestException("Only completed transactions can be refunded");
        }

        original.setStatus(PaymentStatus.REFUNDED);
        original.setFailureReason(reason);
        transactionRepository.save(original);

        return original;
    }

    @Transactional(readOnly = true)
    public Transaction getTransaction(UUID transactionId) {
        return transactionRepository
                .findById(transactionId)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Transaction not found: " + transactionId));
    }

    @Transactional(readOnly = true)
    public Transaction getTransactionByReference(UUID referenceId, String referenceType) {
        return transactionRepository
                .findByReferenceIdAndReferenceType(referenceId, referenceType)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Transaction not found for reference: " + referenceId));
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getPayerTransactions(UUID payerId, Pageable pageable) {
        return transactionRepository.findByPayerId(payerId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getPayeeTransactions(UUID payeeId, Pageable pageable) {
        return transactionRepository.findByPayeeId(payeeId, pageable);
    }
}
