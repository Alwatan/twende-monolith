package com.twende.common.event.loyalty;

import com.twende.common.enums.VehicleType;
import com.twende.common.event.KafkaEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

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
