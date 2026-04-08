package tz.co.twende.subscription.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDto {

    private UUID id;
    private UUID driverId;
    private String countryCode;
    private UUID planId;
    private String status;
    private String paymentMethod;
    private BigDecimal amountPaid;
    private Instant startedAt;
    private Instant expiresAt;
    private UUID paymentRef;
    private Instant createdAt;
}
