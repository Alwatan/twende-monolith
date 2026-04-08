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
public class DriverCandidate {

    private UUID driverId;
    private BigDecimal distanceKm;
    private BigDecimal ratingScore;
    private BigDecimal acceptanceRate;
    private BigDecimal compositeScore;
}
