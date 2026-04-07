package tz.co.twende.common.event.loyalty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import tz.co.twende.common.enums.VehicleType;
import tz.co.twende.common.event.KafkaEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class FreeRideOfferEarnedEvent extends KafkaEvent {
    private UUID offerId;
    private UUID riderId;
    private VehicleType vehicleType;
    private BigDecimal maxDistanceKm;
    private Instant expiresAt;
}
