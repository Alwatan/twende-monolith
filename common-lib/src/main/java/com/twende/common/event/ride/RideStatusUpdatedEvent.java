package com.twende.common.event.ride;

import com.twende.common.enums.RideStatus;
import com.twende.common.event.KafkaEvent;
import java.util.UUID;
import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class RideStatusUpdatedEvent extends KafkaEvent {
    private UUID rideId;
    private RideStatus previousStatus;
    private RideStatus newStatus;
}
