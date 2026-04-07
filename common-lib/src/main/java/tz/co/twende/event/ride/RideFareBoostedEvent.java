package tz.co.twende.common.event.ride;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;
import tz.co.twende.common.event.KafkaEvent;

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
