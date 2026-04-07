package com.twende.common.event.ride;

import com.twende.common.enums.VehicleType;
import com.twende.common.event.KafkaEvent;
import com.twende.common.event.Location;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

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
