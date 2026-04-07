package com.twende.common.event.subscription;

import com.twende.common.enums.SubscriptionPlan;
import com.twende.common.event.KafkaEvent;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

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
