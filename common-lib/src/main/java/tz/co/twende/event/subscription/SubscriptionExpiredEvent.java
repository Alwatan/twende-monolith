package tz.co.twende.common.event.subscription;

import java.util.UUID;
import lombok.*;
import tz.co.twende.common.event.KafkaEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionExpiredEvent extends KafkaEvent {
    private UUID subscriptionId;
    private UUID driverId;
}
