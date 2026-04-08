package tz.co.twende.user.dto;

import java.math.BigDecimal;
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
public class RecentRideDto {
    private UUID rideId;
    private String dropoffAddress;
    private BigDecimal dropoffLat;
    private BigDecimal dropoffLng;
    private Instant completedAt;
    private BigDecimal fare;
    private String currencyCode;
}
