package tz.co.twende.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.enums.PaymentMethod;
import tz.co.twende.common.enums.PaymentStatus;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ConflictException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.payment.dto.request.RidePaymentRequest;
import tz.co.twende.payment.dto.request.SubscriptionPaymentRequest;
import tz.co.twende.payment.dto.request.WithdrawRequest;
import tz.co.twende.payment.entity.DriverWallet;
import tz.co.twende.payment.entity.Transaction;
import tz.co.twende.payment.kafka.PaymentEventPublisher;
import tz.co.twende.payment.provider.PaymentGateway;
import tz.co.twende.payment.provider.PaymentResult;
import tz.co.twende.payment.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private WalletService walletService;
    @Mock private PaymentGateway paymentGateway;
    @Mock private PaymentEventPublisher eventPublisher;

    @InjectMocks private PaymentService paymentService;

    @Test
    void givenFreeRide_whenProcessRidePayment_thenDriverWalletCredited() {
        RidePaymentRequest request = new RidePaymentRequest();
        request.setRideId(UUID.randomUUID());
        request.setRiderId(UUID.randomUUID());
        request.setDriverId(UUID.randomUUID());
        request.setAmount(new BigDecimal("5000.00"));
        request.setCurrencyCode("TZS");
        request.setCountryCode("TZ");
        request.setFreeRide(true);

        when(transactionRepository.existsByReferenceIdAndReferenceType(any(), eq("RIDE")))
                .thenReturn(false);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(walletService.creditDriverWallet(any(), any(), any(), any(), any(), any()))
                .thenReturn(new DriverWallet());

        Transaction result = paymentService.processRidePayment(request);

        assertEquals(PaymentStatus.COMPLETED, result.getStatus());
        verify(walletService)
                .creditDriverWallet(
                        eq(request.getDriverId()),
                        eq(request.getAmount()),
                        eq(request.getRideId()),
                        anyString(),
                        eq("TZ"),
                        eq("TZS"));
        verify(eventPublisher).publishPaymentCompleted(any());
    }

    @Test
    void givenCashRide_whenProcessRidePayment_thenTransactionRecordedNoWalletCredit() {
        RidePaymentRequest request = new RidePaymentRequest();
        request.setRideId(UUID.randomUUID());
        request.setRiderId(UUID.randomUUID());
        request.setDriverId(UUID.randomUUID());
        request.setAmount(new BigDecimal("3000.00"));
        request.setCurrencyCode("TZS");
        request.setCountryCode("TZ");
        request.setFreeRide(false);

        when(transactionRepository.existsByReferenceIdAndReferenceType(any(), eq("RIDE")))
                .thenReturn(false);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = paymentService.processRidePayment(request);

        assertEquals(PaymentStatus.COMPLETED, result.getStatus());
        assertEquals(PaymentMethod.CASH, result.getPaymentMethod());
        verify(walletService, never()).creditDriverWallet(any(), any(), any(), any(), any(), any());
        verify(eventPublisher).publishPaymentCompleted(any());
    }

    @Test
    void givenDuplicateRide_whenProcessRidePayment_thenThrowsConflict() {
        RidePaymentRequest request = new RidePaymentRequest();
        request.setRideId(UUID.randomUUID());
        request.setRiderId(UUID.randomUUID());
        request.setDriverId(UUID.randomUUID());
        request.setAmount(new BigDecimal("3000.00"));
        request.setCurrencyCode("TZS");
        request.setCountryCode("TZ");

        when(transactionRepository.existsByReferenceIdAndReferenceType(any(), eq("RIDE")))
                .thenReturn(true);

        assertThrows(ConflictException.class, () -> paymentService.processRidePayment(request));
    }

    @Test
    void givenSuccessfulSelcom_whenProcessSubscriptionPayment_thenCompleted() {
        SubscriptionPaymentRequest request = new SubscriptionPaymentRequest();
        request.setSubscriptionId(UUID.randomUUID());
        request.setDriverId(UUID.randomUUID());
        request.setAmount(new BigDecimal("2000.00"));
        request.setCurrencyCode("TZS");
        request.setCountryCode("TZ");
        request.setMobileNumber("+255712345678");

        when(transactionRepository.existsByReferenceIdAndReferenceType(any(), eq("SUBSCRIPTION")))
                .thenReturn(false);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGateway.charge(eq("selcom"), any()))
                .thenReturn(PaymentResult.success("SELCOM-REF"));

        Transaction result = paymentService.processSubscriptionPayment(request);

        assertEquals(PaymentStatus.COMPLETED, result.getStatus());
        assertEquals("SELCOM-REF", result.getProviderRef());
    }

    @Test
    void givenSelcomFailure_whenProcessSubscriptionPayment_thenFailed() {
        SubscriptionPaymentRequest request = new SubscriptionPaymentRequest();
        request.setSubscriptionId(UUID.randomUUID());
        request.setDriverId(UUID.randomUUID());
        request.setAmount(new BigDecimal("2000.00"));
        request.setCurrencyCode("TZS");
        request.setCountryCode("TZ");
        request.setMobileNumber("+255712345678");

        when(transactionRepository.existsByReferenceIdAndReferenceType(any(), eq("SUBSCRIPTION")))
                .thenReturn(false);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGateway.charge(eq("selcom"), any()))
                .thenReturn(PaymentResult.failure("Network error"));

        Transaction result = paymentService.processSubscriptionPayment(request);

        assertEquals(PaymentStatus.FAILED, result.getStatus());
        assertEquals("Network error", result.getFailureReason());
    }

    @Test
    void givenSuccessfulDisburse_whenProcessWithdrawal_thenCompleted() {
        UUID driverId = UUID.randomUUID();
        WithdrawRequest request = new WithdrawRequest(new BigDecimal("1000.00"), "+255712345678");

        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(walletService.debitDriverWallet(any(), any(), any(), any()))
                .thenReturn(new DriverWallet());
        when(paymentGateway.disburse(eq("selcom"), any()))
                .thenReturn(PaymentResult.success("SELCOM-D-REF"));

        Transaction result = paymentService.processWithdrawal(driverId, request, "TZ");

        assertEquals(PaymentStatus.COMPLETED, result.getStatus());
        verify(walletService).debitDriverWallet(eq(driverId), any(), any(), any());
        verify(walletService, never()).creditDriverWallet(any(), any(), any(), any(), any(), any());
    }

    @Test
    void givenDisburseFailure_whenProcessWithdrawal_thenWalletReCredited() {
        UUID driverId = UUID.randomUUID();
        WithdrawRequest request = new WithdrawRequest(new BigDecimal("1000.00"), "+255712345678");

        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(walletService.debitDriverWallet(any(), any(), any(), any()))
                .thenReturn(new DriverWallet());
        when(paymentGateway.disburse(eq("selcom"), any()))
                .thenReturn(PaymentResult.failure("Disburse failed"));
        when(walletService.creditDriverWallet(any(), any(), any(), any(), any(), any()))
                .thenReturn(new DriverWallet());

        Transaction result = paymentService.processWithdrawal(driverId, request, "TZ");

        assertEquals(PaymentStatus.FAILED, result.getStatus());
        verify(walletService).debitDriverWallet(eq(driverId), any(), any(), any());
        verify(walletService).creditDriverWallet(eq(driverId), any(), any(), any(), any(), any());
    }

    @Test
    void givenCompletedTransaction_whenRefund_thenStatusRefunded() {
        UUID txId = UUID.randomUUID();
        Transaction tx = new Transaction();
        tx.setStatus(PaymentStatus.COMPLETED);
        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = paymentService.processRefund(txId, "Customer request");

        assertEquals(PaymentStatus.REFUNDED, result.getStatus());
    }

    @Test
    void givenPendingTransaction_whenRefund_thenThrowsBadRequest() {
        UUID txId = UUID.randomUUID();
        Transaction tx = new Transaction();
        tx.setStatus(PaymentStatus.PENDING);
        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        assertThrows(
                BadRequestException.class, () -> paymentService.processRefund(txId, "Should fail"));
    }

    @Test
    void givenNonExistentTransaction_whenGetTransaction_thenThrowsNotFound() {
        UUID txId = UUID.randomUUID();
        when(transactionRepository.findById(txId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> paymentService.getTransaction(txId));
    }
}
