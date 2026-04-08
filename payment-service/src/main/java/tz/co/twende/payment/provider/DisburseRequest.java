package tz.co.twende.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisburseRequest {

    private UUID transactionId;
    private String mobileNumber;
    private BigDecimal amount;
    private String currencyCode;
    private String description;
}
