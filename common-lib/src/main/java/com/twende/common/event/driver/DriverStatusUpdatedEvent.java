package com.twende.common.event.driver;

import com.twende.common.enums.DriverStatus;
import com.twende.common.event.KafkaEvent;
import java.util.UUID;
import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class DriverStatusUpdatedEvent extends KafkaEvent {
    private UUID driverId;
    private DriverStatus previousStatus;
    private DriverStatus newStatus;
}
