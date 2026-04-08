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
public class RideResponse {

    private UUID id;
    private String countryCode;
    private UUID riderId;
    private UUID driverId;
    private String status;
    private String vehicleType;
    private UUID cityId;

    private BigDecimal pickupLat;
    private BigDecimal pickupLng;
    private String pickupAddress;

    private BigDecimal dropoffLat;
    private BigDecimal dropoffLng;
    private String dropoffAddress;

    private BigDecimal estimatedFare;
    private BigDecimal fareBoostAmount;
    private BigDecimal finalFare;
    private String currencyCode;

    private boolean freeRide;
    private UUID freeRideOfferId;

    private int driverRejectionCount;

    private Integer distanceMetres;
    private Integer durationSeconds;

    private Instant requestedAt;
    private Instant assignedAt;
    private Instant arrivedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private String cancelReason;
    private String cancelledBy;

    // Charter / Scheduled booking fields
    private String serviceCategory;
    private String bookingType;
    private Instant scheduledPickupAt;
    private String tripDirection;
    private String qualityTier;
    private Instant returnPickupAt;
    private String paymentTiming;

    private Instant createdAt;
    private Instant updatedAt;
}
