package tz.co.twende.user.kafka.event;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdatedEvent {
    private UUID userId;
    private String fullName;
    private String email;
    private String countryCode;
    private Instant timestamp;
}
