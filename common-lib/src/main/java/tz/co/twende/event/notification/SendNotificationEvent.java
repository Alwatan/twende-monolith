package tz.co.twende.common.event.notification;

import java.util.Map;
import java.util.UUID;
import lombok.*;
import tz.co.twende.common.enums.NotificationType;
import tz.co.twende.common.event.KafkaEvent;

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
