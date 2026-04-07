package tz.co.twende.common.event.ride;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;
import tz.co.twende.common.enums.VehicleType;
import tz.co.twende.common.event.KafkaEvent;
import tz.co.twende.common.event.Location;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class RideRequestedEvent extends KafkaEvent {
    private UUID rideId;
    private UUID riderId;
    private VehicleType vehicleType;
    private Location pickupLocation;
    private Location dropoffLocation;
    private BigDecimal estimatedFare;
}
