package com.twende.common.event.loyalty;

import com.twende.common.event.KafkaEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

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
