package tz.co.twende.ride.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;
import tz.co.twende.common.enums.RideStatus;

@Entity
@Table(name = "rides")
@Getter
@Setter
@NoArgsConstructor
public class Ride extends BaseEntity {

    @Column(name = "rider_id", nullable = false)
    private UUID riderId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RideStatus status;

    @Column(name = "vehicle_type", nullable = false, length = 30)
    private String vehicleType;

    @Column(name = "city_id")
    private UUID cityId;

    // Pickup
    @Column(name = "pickup_lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal pickupLat;

    @Column(name = "pickup_lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal pickupLng;

    @Column(name = "pickup_address", nullable = false, length = 300)
    private String pickupAddress;

    // Dropoff
    @Column(name = "dropoff_lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal dropoffLat;

    @Column(name = "dropoff_lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal dropoffLng;

    @Column(name = "dropoff_address", nullable = false, length = 300)
    private String dropoffAddress;

    // Fare
    @Column(name = "estimated_fare", nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedFare;

    @Column(name = "fare_boost_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal fareBoostAmount = BigDecimal.ZERO;

    @Column(name = "final_fare", precision = 12, scale = 2)
    private BigDecimal finalFare;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    // Loyalty
    @Column(name = "free_ride", nullable = false)
    private boolean freeRide;

    @Column(name = "free_ride_offer_id")
    private UUID freeRideOfferId;

    // Rejection tracking
    @Column(name = "driver_rejection_count", nullable = false)
    private int driverRejectionCount;

    // Trip start OTP
    @Column(name = "trip_start_otp_hash", length = 100)
    private String tripStartOtpHash;

    @Column(name = "trip_start_otp_expires_at")
    private Instant tripStartOtpExpiresAt;

    @Column(name = "trip_start_otp_attempts", nullable = false)
    private int tripStartOtpAttempts;

    // Trip metrics
    @Column(name = "distance_metres")
    private Integer distanceMetres;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    // Timestamps
    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "cancelled_by", length = 10)
    private String cancelledBy;

    @Column(name = "matching_timeout_at")
    private Instant matchingTimeoutAt;

    // Charter / Scheduled booking fields
    @Column(name = "service_category", nullable = false, length = 20)
    private String serviceCategory = "RIDE";

    @Column(name = "booking_type", nullable = false, length = 20)
    private String bookingType = "ON_DEMAND";

    @Column(name = "scheduled_pickup_at")
    private Instant scheduledPickupAt;

    @Column(name = "trip_direction", length = 20)
    private String tripDirection;

    @Column(name = "quality_tier", length = 20)
    private String qualityTier;

    @Column(name = "return_pickup_at")
    private Instant returnPickupAt;

    @Column(name = "payment_timing", nullable = false, length = 20)
    private String paymentTiming = "AT_END";

    // Cargo fields
    @Column(name = "cargo_description", columnDefinition = "TEXT")
    private String cargoDescription;

    @Column(name = "weight_tier", length = 10)
    private String weightTier;

    @Column(name = "driver_provides_loading", nullable = false)
    private boolean driverProvidesLoading;
}
