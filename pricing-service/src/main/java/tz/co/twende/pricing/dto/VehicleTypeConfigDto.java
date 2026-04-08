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
public class VehicleTypeConfigDto {
    private String vehicleType;
    private BigDecimal baseFare;
    private BigDecimal perKm;
    private BigDecimal perMinute;
    private BigDecimal minimumFare;
    private BigDecimal cancellationFee;
    private BigDecimal surgeMultiplierCap;

    // Charter fields
    private BigDecimal perHour;
    private String qualityTier;
    private BigDecimal qualityTierSurcharge;

    // Cargo fields
    private String weightTierSurcharges;
}
