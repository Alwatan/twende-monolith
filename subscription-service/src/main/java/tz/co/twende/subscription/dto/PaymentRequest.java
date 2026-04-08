package tz.co.twende.subscription.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    private UUID driverId;
    private UUID subscriptionId;
    private BigDecimal amount;
    private String currencyCode;
    private String paymentMethod;
    private String countryCode;
}
