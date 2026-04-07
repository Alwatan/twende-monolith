package tz.co.twende.common.event.driver;

import java.util.UUID;
import lombok.*;
import tz.co.twende.common.enums.DriverStatus;
import tz.co.twende.common.event.KafkaEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class DriverStatusUpdatedEvent extends KafkaEvent {
    private UUID driverId;
    private DriverStatus previousStatus;
    private DriverStatus newStatus;
}
