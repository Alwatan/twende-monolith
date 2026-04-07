package tz.co.twende.common.event.subscription;

import java.time.Instant;
import java.util.UUID;
import lombok.*;
import tz.co.twende.common.enums.SubscriptionPlan;
import tz.co.twende.common.event.KafkaEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionActivatedEvent extends KafkaEvent {
    private UUID subscriptionId;
    private UUID driverId;
    private SubscriptionPlan plan;
    private Instant expiresAt;
}
