package tz.co.twende.matching.dto;

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
public class NearbyDriverDto {

    private UUID driverId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal distanceKm;
}
