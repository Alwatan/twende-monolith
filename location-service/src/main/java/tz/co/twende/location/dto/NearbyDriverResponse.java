package tz.co.twende.location.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NearbyDriverResponse {
    private UUID driverId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal distanceKm;
    private int bearing;
    private int speedKmh;
}
