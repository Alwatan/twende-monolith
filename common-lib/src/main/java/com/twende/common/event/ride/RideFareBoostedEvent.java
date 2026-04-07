package com.twende.common.event.ride;

import com.twende.common.event.KafkaEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class RideFareBoostedEvent extends KafkaEvent {
    private UUID rideId;
    private UUID riderId;
    private BigDecimal previousFare;
    private BigDecimal newFare;
    private BigDecimal boostAmount;
}
