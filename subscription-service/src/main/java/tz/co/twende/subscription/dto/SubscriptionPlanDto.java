package tz.co.twende.subscription.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanDto {

    private UUID id;
    private String countryCode;
    private String vehicleType;
    private String planType;
    private BigDecimal price;
    private String currencyCode;
    private int durationHours;
    private String displayName;
}
