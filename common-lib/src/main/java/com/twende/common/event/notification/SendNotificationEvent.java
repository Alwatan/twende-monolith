package com.twende.common.event.notification;

import com.twende.common.enums.NotificationType;
import com.twende.common.event.KafkaEvent;
import java.util.Map;
import java.util.UUID;
import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class SendNotificationEvent extends KafkaEvent {
    private UUID recipientUserId;
    private NotificationType type;
    private String titleKey;
    private String bodyKey;
    private Map<String, String> templateParams;
    private Map<String, String> data;
}
