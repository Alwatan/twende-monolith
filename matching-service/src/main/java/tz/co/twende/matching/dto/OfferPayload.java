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
public class OfferPayload {

    private UUID rideId;
    private UUID driverId;
    private BigDecimal pickupDistanceKm;
    private String pickupAreaName;
    private BigDecimal estimatedTripDistanceKm;
    private int estimatedTripMinutes;
    private BigDecimal totalFare;
    private BigDecimal boostAmount;
    private String currencyCode;
    private int offerWindowSeconds;
}
