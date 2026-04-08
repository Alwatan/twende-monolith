package tz.co.twende.common.event.ride;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import tz.co.twende.common.event.KafkaEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class BookingRequestedEvent extends KafkaEvent {
    private UUID bookingId;
    private UUID riderId;
    private String serviceCategory;
    private String bookingType;
    private String vehicleType;
    private Instant scheduledPickupAt;
    private String qualityTier;
    private String weightTier;
    private boolean driverProvidesLoading;
    private BigDecimal pickupLat;
    private BigDecimal pickupLng;
    private String pickupAddress;
    private BigDecimal dropoffLat;
    private BigDecimal dropoffLng;
    private String dropoffAddress;
    private BigDecimal estimatedFare;
    private String currencyCode;
}
