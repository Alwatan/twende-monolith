package tz.co.twende.loyalty.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLoyaltyRuleRequest {

    @Min(1)
    private Integer requiredRides;

    @DecimalMin("0.01")
    private BigDecimal requiredDistanceKm;

    @DecimalMin("0.01")
    private BigDecimal freeRideMaxDistanceKm;

    @Min(1)
    private Integer offerValidityDays;

    private Boolean isActive;
}
