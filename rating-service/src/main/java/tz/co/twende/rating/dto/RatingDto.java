package tz.co.twende.rating.dto;

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
public class RatingDto {
    private UUID id;
    private UUID rideId;
    private UUID ratedUserId;
    private UUID raterUserId;
    private String raterRole;
    private Short score;
    private String comment;
    private Instant createdAt;
}
