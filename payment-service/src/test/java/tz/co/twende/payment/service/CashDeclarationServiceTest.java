package tz.co.twende.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.exception.ConflictException;
import tz.co.twende.payment.entity.CashDeclaration;
import tz.co.twende.payment.entity.DriverWallet;
import tz.co.twende.payment.kafka.PaymentEventPublisher;
import tz.co.twende.payment.repository.CashDeclarationRepository;
import tz.co.twende.payment.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class CashDeclarationServiceTest {

    @Mock private CashDeclarationRepository cashDeclarationRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private WalletService walletService;
    @Mock private PaymentEventPublisher eventPublisher;

    @InjectMocks private CashDeclarationService cashDeclarationService;

    @Test
    void givenNewRide_whenDeclareCash_thenDeclarationAndTransactionCreated() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("5000.00");

        when(cashDeclarationRepository.existsByRideId(rideId)).thenReturn(false);
        when(cashDeclarationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.existsByReferenceIdAndReferenceType(rideId, "RIDE"))
                .thenReturn(false);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(walletService.creditDriverWallet(any(), any(), any(), any(), any(), any()))
                .thenReturn(new DriverWallet());

        CashDeclaration result =
                cashDeclarationService.declareCash(rideId, driverId, amount, "TZ", "TZS");

        assertNotNull(result);
        assertEquals(rideId, result.getRideId());
        assertEquals(driverId, result.getDriverId());
        assertEquals(amount, result.getAmount());
        verify(walletService)
                .creditDriverWallet(
                        eq(driverId), eq(amount), eq(rideId), anyString(), eq("TZ"), eq("TZS"));
        verify(transactionRepository).save(any());
        verify(eventPublisher).publishPaymentCompleted(any());
    }

    @Test
    void givenDuplicateRide_whenDeclareCash_thenThrowsConflict() {
        UUID rideId = UUID.randomUUID();
        when(cashDeclarationRepository.existsByRideId(rideId)).thenReturn(true);

        assertThrows(
                ConflictException.class,
                () ->
                        cashDeclarationService.declareCash(
                                rideId, UUID.randomUUID(), new BigDecimal("5000.00"), "TZ", "TZS"));
    }

    @Test
    void givenExistingTransaction_whenDeclareCash_thenSkipsTransactionCreation() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        when(cashDeclarationRepository.existsByRideId(rideId)).thenReturn(false);
        when(cashDeclarationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.existsByReferenceIdAndReferenceType(rideId, "RIDE"))
                .thenReturn(true);
        when(walletService.creditDriverWallet(any(), any(), any(), any(), any(), any()))
                .thenReturn(new DriverWallet());

        cashDeclarationService.declareCash(
                rideId, driverId, new BigDecimal("3000.00"), "TZ", "TZS");

        verify(transactionRepository, never()).save(any());
        verify(eventPublisher, never()).publishPaymentCompleted(any());
    }
}
