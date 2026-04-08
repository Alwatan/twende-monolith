package tz.co.twende.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.payment.entity.DriverWallet;
import tz.co.twende.payment.entity.WalletEntry;
import tz.co.twende.payment.repository.DriverWalletRepository;
import tz.co.twende.payment.repository.WalletEntryRepository;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private DriverWalletRepository walletRepository;
    @Mock private WalletEntryRepository walletEntryRepository;

    @InjectMocks private WalletService walletService;

    @Test
    void givenExistingWallet_whenCreditDriverWallet_thenBalanceIncreasedAndEntryCreated() {
        UUID driverId = UUID.randomUUID();
        DriverWallet wallet = new DriverWallet(driverId, "TZ", "TZS");
        wallet.setBalance(new BigDecimal("1000.00"));
        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.creditDriverWallet(
                driverId, new BigDecimal("500.00"), UUID.randomUUID(), "Test credit", "TZ", "TZS");

        assertEquals(new BigDecimal("1500.00"), wallet.getBalance());
        verify(walletRepository).save(wallet);

        ArgumentCaptor<WalletEntry> entryCaptor = ArgumentCaptor.forClass(WalletEntry.class);
        verify(walletEntryRepository).save(entryCaptor.capture());
        WalletEntry entry = entryCaptor.getValue();
        assertEquals("CREDIT", entry.getType());
        assertEquals(new BigDecimal("500.00"), entry.getAmount());
        assertEquals(new BigDecimal("1500.00"), entry.getBalanceAfter());
    }

    @Test
    void givenNoWallet_whenCreditDriverWallet_thenWalletCreatedAndCredited() {
        UUID driverId = UUID.randomUUID();
        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.empty());
        when(walletRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            DriverWallet w = inv.getArgument(0);
                            return w;
                        });

        walletService.creditDriverWallet(
                driverId,
                new BigDecimal("1000.00"),
                UUID.randomUUID(),
                "First credit",
                "TZ",
                "TZS");

        verify(walletRepository, times(2)).save(any(DriverWallet.class));
        verify(walletEntryRepository).save(any(WalletEntry.class));
    }

    @Test
    void givenSufficientBalance_whenDebitDriverWallet_thenBalanceDecreasedAndEntryCreated() {
        UUID driverId = UUID.randomUUID();
        DriverWallet wallet = new DriverWallet(driverId, "TZ", "TZS");
        wallet.setBalance(new BigDecimal("2000.00"));
        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.debitDriverWallet(
                driverId, new BigDecimal("500.00"), UUID.randomUUID(), "Test debit");

        assertEquals(new BigDecimal("1500.00"), wallet.getBalance());
        verify(walletRepository).save(wallet);

        ArgumentCaptor<WalletEntry> entryCaptor = ArgumentCaptor.forClass(WalletEntry.class);
        verify(walletEntryRepository).save(entryCaptor.capture());
        WalletEntry entry = entryCaptor.getValue();
        assertEquals("DEBIT", entry.getType());
        assertEquals(new BigDecimal("500.00"), entry.getAmount());
        assertEquals(new BigDecimal("1500.00"), entry.getBalanceAfter());
    }

    @Test
    void givenInsufficientBalance_whenDebitDriverWallet_thenThrowsBadRequest() {
        UUID driverId = UUID.randomUUID();
        DriverWallet wallet = new DriverWallet(driverId, "TZ", "TZS");
        wallet.setBalance(new BigDecimal("100.00"));
        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.of(wallet));

        assertThrows(
                BadRequestException.class,
                () ->
                        walletService.debitDriverWallet(
                                driverId,
                                new BigDecimal("500.00"),
                                UUID.randomUUID(),
                                "Over debit"));

        verify(walletRepository, never()).save(any());
        verify(walletEntryRepository, never()).save(any());
    }

    @Test
    void givenNoWallet_whenDebitDriverWallet_thenThrowsResourceNotFound() {
        UUID driverId = UUID.randomUUID();
        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () ->
                        walletService.debitDriverWallet(
                                driverId,
                                new BigDecimal("100.00"),
                                UUID.randomUUID(),
                                "No wallet"));
    }

    @Test
    void givenExactBalance_whenDebitDriverWallet_thenBalanceBecomesZero() {
        UUID driverId = UUID.randomUUID();
        DriverWallet wallet = new DriverWallet(driverId, "TZ", "TZS");
        wallet.setBalance(new BigDecimal("500.00"));
        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.debitDriverWallet(
                driverId, new BigDecimal("500.00"), UUID.randomUUID(), "Full debit");

        assertEquals(BigDecimal.ZERO.setScale(2), wallet.getBalance().setScale(2));
    }

    @Test
    void givenExistingWallet_whenGetWallet_thenReturnWallet() {
        UUID driverId = UUID.randomUUID();
        DriverWallet wallet = new DriverWallet(driverId, "TZ", "TZS");
        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.of(wallet));

        DriverWallet result = walletService.getWallet(driverId);

        assertEquals(driverId, result.getDriverId());
    }

    @Test
    void givenNoWallet_whenGetWallet_thenThrowsResourceNotFound() {
        UUID driverId = UUID.randomUUID();
        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> walletService.getWallet(driverId));
    }
}
