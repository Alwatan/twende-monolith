package tz.co.twende.common.event.ride;

import java.util.UUID;
import lombok.*;
import tz.co.twende.common.enums.RideStatus;
import tz.co.twende.common.event.KafkaEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class RideStatusUpdatedEvent extends KafkaEvent {
    private UUID rideId;
    private RideStatus previousStatus;
    private RideStatus newStatus;
}
