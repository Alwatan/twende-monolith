package tz.co.twende.common.event.payment;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import tz.co.twende.common.event.KafkaEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class FlatFeeDeductedEvent extends KafkaEvent {
    private UUID driverId;
    private UUID rideId;
    private BigDecimal fareAmount;
    private BigDecimal feePercentage;
    private BigDecimal feeAmount;
    private String currencyCode;
}
