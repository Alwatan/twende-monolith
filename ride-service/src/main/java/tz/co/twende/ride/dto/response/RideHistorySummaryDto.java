package tz.co.twende.ride.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RideHistorySummaryDto {

    private UUID rideId;
    private BigDecimal pickupLat;
    private BigDecimal pickupLng;
    private String pickupAddress;
    private BigDecimal dropoffLat;
    private BigDecimal dropoffLng;
    private String dropoffAddress;
    private Instant completedAt;
    private String vehicleType;
    private BigDecimal finalFare;
    private String currencyCode;
}
