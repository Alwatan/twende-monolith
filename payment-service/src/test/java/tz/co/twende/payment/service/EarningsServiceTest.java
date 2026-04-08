package tz.co.twende.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.payment.dto.response.EarningsDto;
import tz.co.twende.payment.entity.DriverWallet;
import tz.co.twende.payment.entity.WalletEntry;
import tz.co.twende.payment.repository.DriverWalletRepository;
import tz.co.twende.payment.repository.WalletEntryRepository;

@ExtendWith(MockitoExtension.class)
class EarningsServiceTest {

    @Mock private WalletEntryRepository walletEntryRepository;
    @Mock private DriverWalletRepository walletRepository;

    @InjectMocks private EarningsService earningsService;

    @Test
    void givenCredits_whenGetEarnings_thenSumsCorrectly() {
        UUID driverId = UUID.randomUUID();

        WalletEntry entry1 =
                new WalletEntry(
                        driverId,
                        "CREDIT",
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000.00"),
                        UUID.randomUUID(),
                        "Ride 1");
        WalletEntry entry2 =
                new WalletEntry(
                        driverId,
                        "CREDIT",
                        new BigDecimal("2000.00"),
                        new BigDecimal("3000.00"),
                        UUID.randomUUID(),
                        "Ride 2");

        when(walletEntryRepository.findByDriverIdAndTypeAndCreatedAtAfter(
                        eq(driverId), eq("CREDIT"), any(Instant.class)))
                .thenReturn(List.of(entry1, entry2));

        DriverWallet wallet = new DriverWallet(driverId, "TZ", "TZS");
        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.of(wallet));

        EarningsDto earnings = earningsService.getEarnings(driverId);

        assertEquals(new BigDecimal("3000.00"), earnings.getTodayEarnings());
        assertEquals(2, earnings.getTodayTrips());
        assertEquals("TZS", earnings.getCurrency());
    }

    @Test
    void givenNoCredits_whenGetEarnings_thenReturnsZero() {
        UUID driverId = UUID.randomUUID();

        when(walletEntryRepository.findByDriverIdAndTypeAndCreatedAtAfter(
                        eq(driverId), eq("CREDIT"), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.empty());

        EarningsDto earnings = earningsService.getEarnings(driverId);

        assertEquals(BigDecimal.ZERO, earnings.getTodayEarnings());
        assertEquals(0, earnings.getTodayTrips());
        assertEquals("TZS", earnings.getCurrency());
    }
}
