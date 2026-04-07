package tz.co.twende.common.event.loyalty;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;
import tz.co.twende.common.event.KafkaEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class FreeRideCompletedEvent extends KafkaEvent {
    private UUID rideId;
    private UUID riderId;
    private UUID driverId;
    private UUID freeRideOfferId;
    private BigDecimal fareAmount;
    private String currencyCode;
}
