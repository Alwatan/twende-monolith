package tz.co.twende.countryconfig.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleTypeConfigDto implements Serializable {

    private UUID id;
    private String countryCode;
    private String vehicleType;
    private String displayName;
    private Integer maxPassengers;
    private String iconKey;
    private Boolean isActive;

    // Pricing
    private BigDecimal baseFare;
    private BigDecimal perKm;
    private BigDecimal perMinute;
    private BigDecimal minimumFare;
    private BigDecimal cancellationFee;
    private BigDecimal surgeMultiplierCap;

    private String[] requiredDocs;

    // Charter fields
    private String qualityTier;
    private BigDecimal perHour;
    private BigDecimal qualityTierSurcharge;

    private Instant createdAt;
    private Instant updatedAt;
}
