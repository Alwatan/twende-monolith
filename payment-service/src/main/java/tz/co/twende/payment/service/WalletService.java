package tz.co.twende.payment.service;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.payment.entity.DriverWallet;
import tz.co.twende.payment.entity.WalletEntry;
import tz.co.twende.payment.repository.DriverWalletRepository;
import tz.co.twende.payment.repository.WalletEntryRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final DriverWalletRepository walletRepository;
    private final WalletEntryRepository walletEntryRepository;

    @Transactional
    public DriverWallet creditDriverWallet(
            UUID driverId,
            BigDecimal amount,
            UUID referenceId,
            String description,
            String countryCode,
            String currency) {
        DriverWallet wallet =
                walletRepository
                        .findByDriverId(driverId)
                        .orElseGet(
                                () -> {
                                    DriverWallet newWallet =
                                            new DriverWallet(driverId, countryCode, currency);
                                    return walletRepository.save(newWallet);
                                });

        wallet.setBalance(wallet.getBalance().add(amount));
        wallet.setUpdatedAt(java.time.Instant.now());
        walletRepository.save(wallet);

        WalletEntry entry =
                new WalletEntry(
                        driverId, "CREDIT", amount, wallet.getBalance(), referenceId, description);
        walletEntryRepository.save(entry);

        log.info(
                "Credited wallet for driver={}, amount={}, newBalance={}",
                driverId,
                amount,
                wallet.getBalance());

        return wallet;
    }

    @Transactional
    public DriverWallet debitDriverWallet(
            UUID driverId, BigDecimal amount, UUID referenceId, String description) {
        DriverWallet wallet =
                walletRepository
                        .findByDriverId(driverId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Wallet not found for driver: " + driverId));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BadRequestException(
                    "Insufficient balance. Available: "
                            + wallet.getBalance()
                            + ", Requested: "
                            + amount);
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setUpdatedAt(java.time.Instant.now());
        walletRepository.save(wallet);

        WalletEntry entry =
                new WalletEntry(
                        driverId, "DEBIT", amount, wallet.getBalance(), referenceId, description);
        walletEntryRepository.save(entry);

        log.info(
                "Debited wallet for driver={}, amount={}, newBalance={}",
                driverId,
                amount,
                wallet.getBalance());

        return wallet;
    }

    @Transactional(readOnly = true)
    public DriverWallet getWallet(UUID driverId) {
        return walletRepository
                .findByDriverId(driverId)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Wallet not found for driver: " + driverId));
    }

    @Transactional(readOnly = true)
    public Page<WalletEntry> getWalletEntries(UUID driverId, Pageable pageable) {
        return walletEntryRepository.findByDriverIdOrderByCreatedAtDesc(driverId, pageable);
    }
}
