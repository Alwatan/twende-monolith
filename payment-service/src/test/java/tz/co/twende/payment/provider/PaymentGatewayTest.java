package tz.co.twende.payment.provider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.exception.BadRequestException;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayTest {

    @Mock private PaymentProvider selcomProvider;
    @Mock private PaymentProvider cashProvider;

    private PaymentGateway paymentGateway;

    @BeforeEach
    void setUp() {
        when(selcomProvider.getId()).thenReturn("selcom");
        when(cashProvider.getId()).thenReturn("cash");
        paymentGateway = new PaymentGateway(List.of(selcomProvider, cashProvider));
    }

    @Test
    void givenSelcomProvider_whenCharge_thenDelegatesToSelcom() {
        ChargeRequest request =
                ChargeRequest.builder()
                        .transactionId(UUID.randomUUID())
                        .amount(new BigDecimal("2000.00"))
                        .build();
        when(selcomProvider.charge(any())).thenReturn(PaymentResult.success("REF-123"));

        PaymentResult result = paymentGateway.charge("selcom", request);

        assertTrue(result.isSuccess());
        assertEquals("REF-123", result.getReference());
        verify(selcomProvider).charge(request);
    }

    @Test
    void givenCashProvider_whenCharge_thenDelegatesToCash() {
        ChargeRequest request =
                ChargeRequest.builder()
                        .transactionId(UUID.randomUUID())
                        .amount(new BigDecimal("5000.00"))
                        .build();
        when(cashProvider.charge(any())).thenReturn(PaymentResult.success("CASH-REF"));

        PaymentResult result = paymentGateway.charge("cash", request);

        assertTrue(result.isSuccess());
        verify(cashProvider).charge(request);
    }

    @Test
    void givenUnknownProvider_whenGetProvider_thenThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> paymentGateway.getProvider("mpesa"));
    }

    @Test
    void givenKnownProvider_whenHasProvider_thenReturnsTrue() {
        assertTrue(paymentGateway.hasProvider("selcom"));
        assertTrue(paymentGateway.hasProvider("cash"));
    }

    @Test
    void givenUnknownProvider_whenHasProvider_thenReturnsFalse() {
        assertFalse(paymentGateway.hasProvider("mpesa"));
    }
}
