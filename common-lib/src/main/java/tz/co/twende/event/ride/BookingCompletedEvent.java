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
public class BookingCompletedEvent extends KafkaEvent {
    private UUID bookingId;
    private UUID riderId;
    private UUID driverId;
    private String serviceCategory;
    private String vehicleType;
    private BigDecimal finalFare;
    private String currencyCode;
    private Integer distanceMetres;
    private Integer durationSeconds;
    private Instant startedAt;
    private Instant completedAt;
    private boolean freeRide;
}
