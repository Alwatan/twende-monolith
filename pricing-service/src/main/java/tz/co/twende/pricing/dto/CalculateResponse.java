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
public class CalculateResponse {
    private BigDecimal finalFare;
    private String currency;
    private FareBreakdown fareBreakdown;
}
