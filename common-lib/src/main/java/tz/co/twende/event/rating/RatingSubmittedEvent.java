package tz.co.twende.common.event.rating;

import java.util.UUID;
import lombok.*;
import tz.co.twende.common.event.KafkaEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class RatingSubmittedEvent extends KafkaEvent {
    private UUID rideId;
    private UUID ratedUserId;
    private UUID raterUserId;
    private String raterRole;
    private short score;
}
