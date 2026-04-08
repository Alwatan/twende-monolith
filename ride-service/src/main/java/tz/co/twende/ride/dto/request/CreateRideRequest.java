package tz.co.twende.ride.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateRideRequest {

    @NotBlank private String vehicleType;

    @NotNull
    @DecimalMin(value = "-90.0")
    private BigDecimal pickupLat;

    @NotNull
    @DecimalMin(value = "-180.0")
    private BigDecimal pickupLng;

    @NotBlank private String pickupAddress;

    @NotNull
    @DecimalMin(value = "-90.0")
    private BigDecimal dropoffLat;

    @NotNull
    @DecimalMin(value = "-180.0")
    private BigDecimal dropoffLng;

    @NotBlank private String dropoffAddress;

    private UUID cityId;

    // Charter / Scheduled booking fields (optional, defaults to RIDE/ON_DEMAND)
    private String serviceCategory;
    private String bookingType;
    private Instant scheduledPickupAt;
    private String tripDirection;
    private String qualityTier;
    private Instant returnPickupAt;
    private String paymentTiming;

    // Cargo fields (optional, only for CARGO service category)
    private String cargoDescription;
    private String weightTier;
    private Boolean driverProvidesLoading;
}
