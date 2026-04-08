package tz.co.twende.matching.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceBookingDto {
    private UUID bookingId;
    private String serviceCategory;
    private String vehicleType;
    private String qualityTier;
    private Instant scheduledPickupAt;
    private String pickupAddress;
    private String dropoffAddress;
    private BigDecimal estimatedFare;
    private String currencyCode;
    private String tripDirection;

    // Cargo fields
    private String weightTier;
    private String cargoDescription;
    private boolean driverProvidesLoading;
}
