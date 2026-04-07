package com.twende.common.event.subscription;

import com.twende.common.event.KafkaEvent;
import java.util.UUID;
import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionExpiredEvent extends KafkaEvent {
    private UUID subscriptionId;
    private UUID driverId;
}
