package tz.co.twende.payment.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelcomProviderTest {

    private SelcomProvider selcomProvider;

    @BeforeEach
    void setUp() {
        selcomProvider = new SelcomProvider("https://test.selcom.net/v1", "test-key");
    }

    @Test
    void givenSelcomProvider_whenGetId_thenReturnsSelcom() {
        assertEquals("selcom", selcomProvider.getId());
    }

    @Test
    void givenChargeRequest_whenCharge_thenReturnsSuccess() {
        ChargeRequest request =
                ChargeRequest.builder()
                        .transactionId(UUID.randomUUID())
                        .mobileNumber("+255712345678")
                        .amount(new BigDecimal("2000.00"))
                        .currencyCode("TZS")
                        .description("Test charge")
                        .build();

        PaymentResult result = selcomProvider.charge(request);

        assertTrue(result.isSuccess());
        assertNotNull(result.getReference());
        assertTrue(result.getReference().startsWith("SELCOM-"));
    }

    @Test
    void givenDisburseRequest_whenDisburse_thenReturnsSuccess() {
        DisburseRequest request =
                DisburseRequest.builder()
                        .transactionId(UUID.randomUUID())
                        .mobileNumber("+255712345678")
                        .amount(new BigDecimal("5000.00"))
                        .currencyCode("TZS")
                        .description("Test disburse")
                        .build();

        PaymentResult result = selcomProvider.disburse(request);

        assertTrue(result.isSuccess());
        assertNotNull(result.getReference());
        assertTrue(result.getReference().startsWith("SELCOM-D-"));
    }
}
