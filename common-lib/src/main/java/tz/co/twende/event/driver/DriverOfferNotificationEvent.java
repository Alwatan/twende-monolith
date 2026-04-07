package tz.co.twende.common.event.driver;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;
import tz.co.twende.common.event.KafkaEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class DriverOfferNotificationEvent extends KafkaEvent {
    private UUID rideId;
    private UUID driverId;
    private double pickupDistanceKm;
    private String pickupAreaName;
    private double estimatedTripDistanceKm;
    private int estimatedTripMinutes;
    private BigDecimal totalFare;
    private BigDecimal boostAmount;
    private String currencyCode;
    private int offerWindowSeconds;
}
