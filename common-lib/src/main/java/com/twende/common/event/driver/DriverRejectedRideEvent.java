package com.twende.common.event.driver;

import com.twende.common.event.KafkaEvent;
import java.util.UUID;
import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class DriverRejectedRideEvent extends KafkaEvent {
    private UUID rideId;
    private UUID driverId;
    private int newRejectionCount;
}
