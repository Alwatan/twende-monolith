package tz.co.twende.payment.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.payment.dto.request.CashDeclareRequest;
import tz.co.twende.payment.dto.request.WithdrawRequest;
import tz.co.twende.payment.dto.response.EarningsDto;
import tz.co.twende.payment.dto.response.TransactionDto;
import tz.co.twende.payment.dto.response.WalletDto;
import tz.co.twende.payment.dto.response.WalletEntryDto;
import tz.co.twende.payment.entity.DriverWallet;
import tz.co.twende.payment.entity.WalletEntry;
import tz.co.twende.payment.mapper.PaymentMapper;
import tz.co.twende.payment.service.CashDeclarationService;
import tz.co.twende.payment.service.EarningsService;
import tz.co.twende.payment.service.PaymentService;
import tz.co.twende.payment.service.WalletService;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentDriverController {

    private final WalletService walletService;
    private final PaymentService paymentService;
    private final CashDeclarationService cashDeclarationService;
    private final EarningsService earningsService;
    private final PaymentMapper paymentMapper;

    @GetMapping("/wallet")
    public ResponseEntity<ApiResponse<WalletDto>> getWallet(
            @RequestHeader("X-User-Id") UUID driverId) {
        DriverWallet wallet = walletService.getWallet(driverId);
        List<WalletEntry> entries =
                walletService
                        .getWalletEntries(
                                driverId,
                                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")))
                        .getContent();
        List<WalletEntryDto> entryDtos = paymentMapper.toWalletEntryDtoList(entries);

        WalletDto dto =
                WalletDto.builder()
                        .driverId(wallet.getDriverId())
                        .balance(wallet.getBalance())
                        .currency(wallet.getCurrency())
                        .recentEntries(entryDtos)
                        .build();

        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @GetMapping("/earnings")
    public ResponseEntity<ApiResponse<EarningsDto>> getEarnings(
            @RequestHeader("X-User-Id") UUID driverId) {
        return ResponseEntity.ok(ApiResponse.ok(earningsService.getEarnings(driverId)));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<ApiResponse<TransactionDto>> withdraw(
            @RequestHeader("X-User-Id") UUID driverId,
            @RequestHeader("X-Country-Code") String countryCode,
            @Valid @RequestBody WithdrawRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        paymentMapper.toTransactionDto(
                                paymentService.processWithdrawal(driverId, request, countryCode))));
    }

    @PostMapping("/{rideId}/cash-declare")
    public ResponseEntity<ApiResponse<Void>> declareCash(
            @PathVariable UUID rideId,
            @RequestHeader("X-User-Id") UUID driverId,
            @RequestHeader("X-Country-Code") String countryCode,
            @Valid @RequestBody CashDeclareRequest request) {
        cashDeclarationService.declareCash(
                rideId, driverId, request.getAmount(), countryCode, "TZS");
        return ResponseEntity.ok(ApiResponse.ok(null, "Cash declared successfully"));
    }
}
