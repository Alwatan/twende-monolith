package tz.co.twende.payment.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.payment.dto.request.RefundRequest;
import tz.co.twende.payment.dto.request.RidePaymentRequest;
import tz.co.twende.payment.dto.request.SubscriptionPaymentRequest;
import tz.co.twende.payment.dto.response.TransactionDto;
import tz.co.twende.payment.mapper.PaymentMapper;
import tz.co.twende.payment.service.PaymentService;

@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class PaymentInternalController {

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;

    @PostMapping("/ride")
    public ResponseEntity<ApiResponse<TransactionDto>> processRidePayment(
            @Valid @RequestBody RidePaymentRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        paymentMapper.toTransactionDto(
                                paymentService.processRidePayment(request))));
    }

    @PostMapping("/subscription")
    public ResponseEntity<ApiResponse<TransactionDto>> processSubscriptionPayment(
            @Valid @RequestBody SubscriptionPaymentRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        paymentMapper.toTransactionDto(
                                paymentService.processSubscriptionPayment(request))));
    }

    @PostMapping("/refund")
    public ResponseEntity<ApiResponse<TransactionDto>> processRefund(
            @Valid @RequestBody RefundRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        paymentMapper.toTransactionDto(
                                paymentService.processRefund(
                                        request.getTransactionId(), request.getReason()))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransactionDto>> getTransaction(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.ok(paymentMapper.toTransactionDto(paymentService.getTransaction(id))));
    }
}
