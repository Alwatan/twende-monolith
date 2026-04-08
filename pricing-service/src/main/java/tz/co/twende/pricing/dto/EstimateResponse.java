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
public class EstimateResponse {
    private BigDecimal estimatedFare;
    private String currency;
    private String displayFare;
    private int distanceMetres;
    private int durationSeconds;
    private BigDecimal surgeMultiplier;
    private FareBreakdown fareBreakdown;
}
