package tz.co.twende.pricing.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareBreakdown {
    private BigDecimal baseFare;
    private BigDecimal distanceFare;
    private BigDecimal timeFare;
    private BigDecimal surgeFare;
    private BigDecimal airportSurcharge;
    private boolean minimumFareApplied;
}
