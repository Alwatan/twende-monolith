package tz.co.twende.payment.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.payment.dto.response.EarningsDto;
import tz.co.twende.payment.entity.DriverWallet;
import tz.co.twende.payment.entity.WalletEntry;
import tz.co.twende.payment.repository.DriverWalletRepository;
import tz.co.twende.payment.repository.WalletEntryRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class EarningsService {

    private final WalletEntryRepository walletEntryRepository;
    private final DriverWalletRepository walletRepository;

    @Transactional(readOnly = true)
    public EarningsDto getEarnings(UUID driverId) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        Instant startOfDay = now.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfWeek =
                now.with(ChronoField.DAY_OF_WEEK, 1)
                        .toLocalDate()
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant();
        Instant startOfMonth =
                now.withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();

        List<WalletEntry> todayCredits =
                walletEntryRepository.findByDriverIdAndTypeAndCreatedAtAfter(
                        driverId, "CREDIT", startOfDay);
        List<WalletEntry> weekCredits =
                walletEntryRepository.findByDriverIdAndTypeAndCreatedAtAfter(
                        driverId, "CREDIT", startOfWeek);
        List<WalletEntry> monthCredits =
                walletEntryRepository.findByDriverIdAndTypeAndCreatedAtAfter(
                        driverId, "CREDIT", startOfMonth);

        String currency =
                walletRepository
                        .findByDriverId(driverId)
                        .map(DriverWallet::getCurrency)
                        .orElse("TZS");

        return EarningsDto.builder()
                .todayEarnings(sumAmounts(todayCredits))
                .weekEarnings(sumAmounts(weekCredits))
                .monthEarnings(sumAmounts(monthCredits))
                .currency(currency)
                .todayTrips(todayCredits.size())
                .weekTrips(weekCredits.size())
                .monthTrips(monthCredits.size())
                .build();
    }

    private BigDecimal sumAmounts(List<WalletEntry> entries) {
        return entries.stream()
                .map(WalletEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
