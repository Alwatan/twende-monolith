package tz.co.twende.ride.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RideStatusEventDto {

    private String fromStatus;
    private String toStatus;
    private UUID actorId;
    private String actorRole;
    private Instant occurredAt;
}
