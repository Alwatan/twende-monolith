package tz.co.twende.common.event.driver;

import java.util.UUID;
import lombok.*;
import tz.co.twende.common.event.KafkaEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class RideOfferAcceptedEvent extends KafkaEvent {
    private UUID rideId;
    private UUID driverId;
    private int estimatedArrivalSeconds;
}
