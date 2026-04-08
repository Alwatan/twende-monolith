package tz.co.twende.payment.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.payment.dto.response.TransactionDto;
import tz.co.twende.payment.mapper.PaymentMapper;
import tz.co.twende.payment.service.PaymentService;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentRiderController {

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<Page<TransactionDto>>> getPaymentHistory(
            @RequestHeader("X-User-Id") UUID riderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<TransactionDto> transactions =
                paymentService
                        .getPayerTransactions(
                                riderId,
                                PageRequest.of(
                                        page,
                                        Math.min(size, 100),
                                        Sort.by(Sort.Direction.DESC, "initiatedAt")))
                        .map(paymentMapper::toTransactionDto);
        return ResponseEntity.ok(ApiResponse.ok(transactions));
    }

    @GetMapping("/{rideId}")
    public ResponseEntity<ApiResponse<TransactionDto>> getRidePayment(@PathVariable UUID rideId) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        paymentMapper.toTransactionDto(
                                paymentService.getTransactionByReference(rideId, "RIDE"))));
    }
}
