package tz.co.twende.common.event.ride;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import tz.co.twende.common.event.KafkaEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class RideCompletedEvent extends KafkaEvent {
    private UUID rideId;
    private UUID riderId;
    private UUID driverId;
    private BigDecimal finalFare;
    private Integer distanceMetres;
    private Integer durationSeconds;
    private Instant startedAt;
    private Instant completedAt;
    private boolean freeRide;
    private UUID freeRideOfferId;
}
