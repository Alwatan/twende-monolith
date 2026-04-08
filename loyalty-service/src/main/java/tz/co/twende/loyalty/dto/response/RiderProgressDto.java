package tz.co.twende.loyalty.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiderProgressDto {
    private UUID id;
    private UUID riderId;
    private String countryCode;
    private String vehicleType;
    private int rideCount;
    private BigDecimal totalDistanceKm;
    private Instant lastResetAt;
}
