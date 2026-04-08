package tz.co.twende.payment.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CashProviderTest {

    private final CashProvider cashProvider = new CashProvider();

    @Test
    void givenCashProvider_whenGetId_thenReturnsCash() {
        assertEquals("cash", cashProvider.getId());
    }

    @Test
    void givenChargeRequest_whenCharge_thenReturnsSuccess() {
        ChargeRequest request =
                ChargeRequest.builder()
                        .transactionId(UUID.randomUUID())
                        .amount(new BigDecimal("3000.00"))
                        .build();

        PaymentResult result = cashProvider.charge(request);

        assertTrue(result.isSuccess());
        assertNotNull(result.getReference());
    }

    @Test
    void givenDisburseRequest_whenDisburse_thenReturnsFailure() {
        DisburseRequest request =
                DisburseRequest.builder()
                        .transactionId(UUID.randomUUID())
                        .amount(new BigDecimal("1000.00"))
                        .build();

        PaymentResult result = cashProvider.disburse(request);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }
}
