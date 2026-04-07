package com.twende.common.event.driver;

import com.twende.common.event.KafkaEvent;
import java.util.UUID;
import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class DriverMatchedEvent extends KafkaEvent {
    private UUID rideId;
    private UUID driverId;
    private UUID riderId;
    private Integer estimatedArrivalSeconds;
}
