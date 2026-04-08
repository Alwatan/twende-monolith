package tz.co.twende.rating.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RideDetailsDto {
    private UUID id;
    private UUID riderId;
    private UUID driverId;
    private String status;
    private String countryCode;
    private Instant completedAt;
}
