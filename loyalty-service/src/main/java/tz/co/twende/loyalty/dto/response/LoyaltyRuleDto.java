package tz.co.twende.loyalty.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyRuleDto {
    private UUID id;
    private String countryCode;
    private String vehicleType;
    private int requiredRides;
    private BigDecimal requiredDistanceKm;
    private BigDecimal freeRideMaxDistanceKm;
    private int offerValidityDays;
    private boolean active;
}
